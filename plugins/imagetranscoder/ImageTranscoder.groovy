/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Copyright 2010 bemoko 
 */
package imagetranscoder

import com.bemoko.live.platform.mwc.plugins.AbstractPlugin

import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.Transparency
import java.awt.RenderingHints
import java.awt.image.ConvolveOp
import java.awt.image.Kernel

/*
 * Image Transcoder
 */
class ImageTranscoder extends AbstractPlugin {  

  /*
   * Progressive downscaling is slower but better.  This comes into play
   * if downscaling by more than 50%
   */
  private static PROGRESSIVE_DOWNSCALING=true
  private static PROGRESSIVE_DOWNSCALING_FACTOR=2
  private static COMPRESSION_PREPEND_KEY="b-"
  
  /*
   * URI compression rules to shorten URLs.
   */
  def uriCompressionRules =  [
     "http":"http://"
   ]
      
  def rules = { image, rule ->
    switch (rule) {
      // Scale to width rule, e.g. "100.jpeg"
      case ~/[0-9]*\.jpeg/ :
        def m = rule =~ /([0-9]*)\.jpeg/
        return scaleToSize(image,m[0][1] as int)        
      // Scale to size rule, e.g. "120x60.jpeg"
      case ~/[0-9]*x[0-9]*\.jpeg/ :
        def m = rule =~ /([0-9]*)x([0-9]*)\.jpeg/
        return scaleToSize(image,m[0][1] as int,m[0][2] as int)        
      // Crop to width rule, e.g. "crop100.jpeg"
      case ~/crop[0-9]*\.jpeg/ :
        def m = rule =~ /crop([0-9]*)\.jpeg/
        return cropToSize(image,m[0][1] as int)        
      // Resize to width and then crop to width rule, e.g. "150-crop100.jpeg"
      case ~/[0-9]*-crop[0-9]*\.jpeg/ :
        def m = rule =~ /([0-9]*)-crop([0-9]*)\.jpeg/
        return cropToSize(scaleToSize(image,m[0][1] as int),m[0][2] as int)
      default :
        throw new Exception("Rule $rule not recognised")
    }  
  }



  /* 
   * Get transcoded image uri for a given image input uri
   */
  def uri(rule,uri) {
    "/t/rule/${rule}?uri=${compressUri(uri)}" + 
      (platform.intent.reload ? "&reload=${platform.intent.reload}" : "")
  }
  
  /*
   * Not recommended as a scaling, but included as basic example
   */
  def scaleNearestNeighbor(imageIn,factor) {
    def transform = new AffineTransform()
    transform.scale(factor,factor)
    def op = new AffineTransformOp(transform,AffineTransformOp.TYPE_NEAREST_NEIGHBOR )
    def imageOut=op.filter(imageIn,null)  
  }
  
  static scaleByFactor(imageIn,factor) {
    return scaleToSize(imageIn,imageIn.width * factor,imageIn.height * factor)
  }

  /*
   * Scale to size always maintaining aspect ratio and never up sizing
   * 
   * Multi step bilinear scaling as recommended at
   * http://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html
   */
  def scaleToSize(imageIn,int targetWidth,int targetHeight=-1,graphicsCallback=null) {
    int type = (imageIn.getTransparency() == Transparency.OPAQUE) ?
      BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    def (width,height) = [imageIn.width,imageIn.height]
    if (targetWidth > width) targetWidth=width
    if (targetHeight > height) targetHeight=height    
    def imageOut=drawImage(imageIn,width,height,width,height,type,null)
    int nextInputWidth, nextInputHeight, nextTargetWidth, nextTargetHeight    
    nextInputWidth=width
    nextInputHeight=height
    while (true) {
      boolean finalRendering=false
      if (PROGRESSIVE_DOWNSCALING && 
          (targetWidth < nextInputWidth/PROGRESSIVE_DOWNSCALING_FACTOR)) {
        nextTargetWidth=nextInputWidth/PROGRESSIVE_DOWNSCALING_FACTOR
        nextTargetHeight=nextInputHeight/PROGRESSIVE_DOWNSCALING_FACTOR
      } else {
        finalRendering=true
        nextTargetWidth=targetWidth
        nextTargetHeight=targetHeight        
      }      
      imageOut=drawImage(imageOut,nextTargetWidth,nextTargetHeight,
        nextInputWidth,nextInputHeight,type,graphicsCallback,finalRendering)
      if (finalRendering) {
        return imageOut
      } else {
        nextInputWidth=nextTargetWidth
        nextInputHeight=nextTargetHeight
      }
    }
  }
  
  def drawImage(imageIn,int targetWidth,int targetHeight,
      int originalWidth,int originalHeight,type,graphicsCallback,finalRendering=false) {
    /*
     * If target height is less than 0 then we want height calculated from width scaled by 
     * aspect ratio
     */
    if (targetHeight == -1) {
      targetHeight=originalHeight * targetWidth / originalWidth
    }
    /*
     * Determine uncropped width and height
     */
    int uncroppedWidth,uncroppedHeight
    if ( (targetWidth/targetHeight) > (originalWidth/originalHeight) ) {
      /*
       * Target image is more landscape => vertical cropping
       */
       uncroppedWidth = targetWidth
       uncroppedHeight = targetWidth * originalHeight / originalWidth
    } else {
      /*
       * Target image is more portrait => horizontal cropping
       */
       uncroppedWidth = targetHeight * originalWidth / originalHeight
       uncroppedHeight = targetHeight
    }
    BufferedImage imageOut = new BufferedImage(targetWidth, targetHeight, type)
    Graphics2D g2 = imageOut.createGraphics()    
    try {
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      if (true) {
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);            
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
            RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);              
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
            RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
      }
      g2.drawImage(imageIn, 0, 0, uncroppedWidth,uncroppedHeight as int, null)
      /*
       * Allow the transcoding rule to post process image, e.g. overlay
       */
      if (finalRendering && graphicsCallback) {
        graphicsCallback(g2)
      }
    } finally {
      g2.dispose()
    }
    return imageOut
  }
  
  def cropToSize(imageIn,int targetWidth) {
    int width=imageIn.width
    if (width > targetWidth) {
      int margin = (width - targetWidth) / 2
      return crop(imageIn,margin,0,targetWidth)
    } else {
      return imageIn
    }    
  }
  
  def crop(imageIn,int x, int y, int targetWidth=-1,int targetHeight=-1) {
    int width=targetWidth > 0 ? targetWidth : (imageIn.width - x)
    int height=targetHeight > 0 ? targetHeight : (imageIn.height - y)    
    BufferedImage imageOut = new BufferedImage(width, height, getType(imageIn))
    Graphics2D g2 = imageOut.createGraphics()  
    g2.drawImage(imageIn, -x, -y, imageIn.width,imageIn.height, null)  
    return imageOut
  }
  
  def getType(image) {
    (image.getTransparency() == Transparency.OPAQUE) ?
      BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB
  }
  
  /*
   * Compress a URI
   */
  def compressUri(uri) {
    def compressedUri=uri
    for (compressionRule in uriCompressionRules) {
      if (compressedUri.startsWith(compressionRule.value)) {
        compressedUri = COMPRESSION_PREPEND_KEY + compressionRule.key + "-" +    
          compressedUri.substring(compressionRule.value.length())
        break
      }
    }
    return compressedUri
  }
  
  
  /*
   * Uncompress a URI
   */
  def uncompressUri(uri) {
    def uncompressedUri=uri
    def m = (uri =~ /^b-([^-]*)-(.*)/)
    if (m.matches()) {
      uncompressedUri=uriCompressionRules[m[0][1]] + m[0][2]
    }
  }  
}