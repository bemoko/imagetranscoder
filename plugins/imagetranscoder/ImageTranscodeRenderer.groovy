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
 
/*
 * Simple image transcoder
 */
package imagetranscoder

import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream
import javax.imageio.IIOImage

import com.bemoko.live.platform.Bemoko
import com.bemoko.live.platform.ui.Resource
import com.bemoko.live.platform.mwc.plugins.AbstractRendererPlugin
import com.bemoko.live.platform.ui.Resource

class ImageTranscodeRenderer extends AbstractRendererPlugin {

  def log = Bemoko.log
  def transcoder = Bemoko.plugins.transcoder
  def resource, resourcePath  
  
  void execute(Map p) { 
    try {
      def resourcePath = getResourcePath(p)
      if (resourcePath && new File(resourcePath).exists()) {
        resource = new Resource(resourcePath,platform.contentTypeUtils
          .getContentTypeFromPath(resourcePath))      
      } else {
        if (!resourcePath) {
          log.error "Resource path of transcoded image is null"
        } else {
          log.error "Cannot find transcoded image path : $resourcePath"
        }
      }
    } catch (e) {
      log.error "Cannot deliver transcoded image ${p.key}; ${p.rule}; ${p.uri}",e
    }
  }
  
  def getResourcePath(p) {
    if (log.debugEnabled) log.debug "Image URI is : " + transcoder.uncompressUri(p.uri)
    def key = p.uri.toLowerCase().replaceAll("[^a-z0-9]","-")
    if (log.traceEnabled) log.trace "Image key is : " + key
    def fileName = "${Bemoko.config.transcoder.cache.root}/${key}/${p.rule}"
    
    if (!new File(fileName).exists() || "true".equals(p.reload)) {
      def imageOut = transcode(p.uri,p.rule,fileName)
    } else {
      if (log.infoEnabled) log.info "Getting transcoded image from ${fileName}"
    }
    return fileName
  }

  private transcode(uri,rule,fileName) {
    def imageIn = getImage(uri)
    def imageOut = transcoder.rules(imageIn,rule)
    if (imageOut) {
      storeImage(imageOut,fileName)
      return imageOut
    }
    throw new Exception("Unable to trancode : ${uri} ; ${rule} ; ${fileName}")
  }
  
  /*
   * Get the image object at the given URI
   */
  private getImage(uri) {
    def imageUrlAsString
    if (uri.startsWith('/')) {
      /*
       * Local site image
       */
      imageUrlAsString=platform.intent.platformEndPoint + "/" +
        platform.site.name + uri
    } else {
      imageUrlAsString = transcoder.uncompressUri(uri)
    }
    try {
      ImageIO.read(new URL(imageUrlAsString))
    } catch (java.net.MalformedURLException e) {
      throw new Exception("Can't read image from ${imageUrlAsString}",e)
    }
  }

  /*
   * If we need explicit control in storeImage we might want to do ...
   */
  private storeImage(image,fileName) {
    if (log.infoEnabled) log.info "Transcoding image saved to ${fileName}"
    def writers = ImageIO.getImageWritersByFormatName("jpeg")
    def writer = writers.next()
    def iwp = writer.getDefaultWriteParam()
    iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
    iwp.setCompressionQuality(0.8f)
    def file=new File(fileName)
    file.parentFile.mkdirs()
    FileImageOutputStream output = new FileImageOutputStream(file);
    writer.setOutput(output);
    IIOImage iioImage = new IIOImage(image, null, null)
    writer.write(null, iioImage, iwp)
    writer.dispose()
  }
  
  void close() {
    if (resource) resource.close()
  }

  String getContentType() { resource?.contentType }
  long getContentLength() { resource ? resource.length : 0 }
  InputStream getInputStream() { resource?.inputStream }  
}