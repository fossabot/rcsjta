/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010-2016 Orange.
 * Copyright (C) 2014 Sony Mobile Communications AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications AB.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.core.ims.service.im.filetransfer;

import static com.gsma.rcs.utils.StringUtils.UTF8;
import static com.gsma.rcs.utils.StringUtils.UTF8_STR;

import com.gsma.rcs.core.FileAccessException;
import com.gsma.rcs.core.ParseFailureException;
import com.gsma.rcs.core.content.ContentManager;
import com.gsma.rcs.core.content.MmContent;
import com.gsma.rcs.core.ims.network.sip.Multipart;
import com.gsma.rcs.core.ims.protocol.PayloadException;
import com.gsma.rcs.core.ims.protocol.sip.SipRequest;
import com.gsma.rcs.core.ims.service.im.chat.ChatMessage;
import com.gsma.rcs.core.ims.service.im.chat.ChatUtils;
import com.gsma.rcs.core.ims.service.im.filetransfer.http.FileTransferHttpInfoDocument;
import com.gsma.rcs.core.ims.service.im.filetransfer.http.FileTransferHttpThumbnail;
import com.gsma.rcs.core.ims.service.im.filetransfer.http.FileTransferXmlParser;
import com.gsma.rcs.platform.AndroidFactory;
import com.gsma.rcs.platform.file.FileDescription;
import com.gsma.rcs.platform.file.FileFactory;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.Base64;
import com.gsma.rcs.utils.CloseableUtils;
import com.gsma.rcs.utils.FileUtils;
import com.gsma.rcs.utils.MimeManager;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.filetransfer.FileTransfer.Disposition;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Utility class to manage File Transfer
 * 
 * @author YPLO6403
 */
public class FileTransferUtils {

    private static final Logger logger = Logger.getLogger(FileTransferUtils.class.getName());

    private static final String FILEICON_INFO = "thumbnail";

    private static final String FILE_INFO = "file";

    private static final String FILEICON_MIMETYPE = "image/jpeg";

    /**
     * Is a file transfer HTTP event type
     * 
     * @param mime MIME type
     * @return Boolean
     */
    public static boolean isFileTransferHttpType(String mime) {
        return mime != null
                && mime.toLowerCase().startsWith(FileTransferHttpInfoDocument.MIME_TYPE);
    }

    /**
     * Create a content of fileIcon from a file
     * 
     * @param file Uri of the image
     * @param fileIconId the identifier of the file icon
     * @param rcsSettings the RCS settings accessor
     * @return the content of the file icon
     * @throws FileAccessException
     */
    public static MmContent createFileicon(Uri file, String fileIconId, RcsSettings rcsSettings)
            throws FileAccessException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = null;
        MmContent fileIcon = null;
        try {
            in = AndroidFactory.getApplicationContext().getContentResolver().openInputStream(file);
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) {
                if (logger.isActivated()) {
                    logger.warn("Cannot decode image " + file);
                }
                return null;
            }
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            long size = FileUtils.getFileSize(AndroidFactory.getApplicationContext(), file);
            // Resize the bitmap
            float scale = 0.05f;
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);

            // Recreate the new bitmap
            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

            // Compress the file to be under the limit
            int quality = 90;
            long maxSize = rcsSettings.getMaxFileIconSize();
            while (size > maxSize) {
                out = new ByteArrayOutputStream();
                resizedBitmap.compress(CompressFormat.JPEG, quality, out);
                out.flush();
                out.close();
                size = out.size();
                quality -= 10;
            }
            // Create fileIcon URL
            String fileIconName = buildFileiconUrl(fileIconId, FILEICON_MIMETYPE);
            // Get the fileIcon data
            byte[] fileIconData = out.toByteArray();

            // Generate fileIcon content
            Uri fileIconUri = Uri.fromFile(new File(rcsSettings.getFileIconRootDirectory().concat(
                    fileIconName)));
            fileIcon = ContentManager.createMmContent(fileIconUri, FILEICON_MIMETYPE,
                    fileIconData.length, fileIconName);
            // persist the fileIcon content
            fileIcon.writeData2File(fileIconData);
            if (logger.isActivated()) {
                logger.debug("Generate Icon " + fileIconName + " for image " + file);
            }
            return fileIcon;

        } catch (IOException e) {
            throw new FileAccessException("Failed to create icon for uri: " + file, e);

        } finally {
            CloseableUtils.tryToClose(in);
            if (fileIcon != null) {
                fileIcon.closeFile();
            }
        }
    }

    /**
     * Generate a filename for the file icon
     * 
     * @param msgId the message ID of the File Transfer
     * @param mimeType the mime-type
     * @return the filename of the file icon
     */
    public static String buildFileiconUrl(String msgId, String mimeType) {
        StringBuilder iconName = new StringBuilder("thumbnail_");
        iconName.append(msgId);
        String extension = MimeManager.getInstance().getExtensionFromMimeType(mimeType);
        if (extension != null) {
            iconName.append(".");
            iconName.append(extension);
            return iconName.toString();
        }
        throw new IllegalArgumentException("Invalid mime type for image");
    }

    /**
     * Extract file icon from incoming INVITE request
     * 
     * @param request Request
     * @param rcsSettings the RCS settings accessor
     * @return fileIcon the file icon content persisted on disk
     * @throws FileAccessException
     */
    public static MmContent extractFileIcon(SipRequest request, RcsSettings rcsSettings)
            throws FileAccessException {
        MmContent result = null;
        try {
            String content = request.getContent();
            String boundary = request.getBoundaryContentType();
            Multipart multi = new Multipart(content, boundary);
            if (!multi.isMultipart()) {
                return null;
            }
            String mimeType = FILEICON_MIMETYPE;
            String data = multi.getPart(mimeType);
            if (data == null) {
                mimeType = "image/png";
                data = multi.getPart(mimeType);
            }
            if (data == null) {
                return null;
            }
            String iconName = buildFileiconUrl(ChatUtils.getContributionId(request), mimeType);
            Uri fileIconUri = Uri.fromFile(new File(rcsSettings.getFileIconRootDirectory().concat(
                    iconName)));
            byte[] fileIconData = Base64.decodeBase64(mimeType.getBytes(UTF8));
            result = ContentManager.createMmContent(fileIconUri, mimeType, fileIconData.length,
                    iconName);
            result.writeData2File(fileIconData);
            return result;

        } finally {
            if (result != null) {
                result.closeFile();
            }
        }

    }

    /**
     * Parse a file transfer over HTTP document
     * 
     * @param xml XML document
     * @param rcsSettings RCS settings
     * @return File transfer document
     * @throws PayloadException
     */
    public static FileTransferHttpInfoDocument parseFileTransferHttpDocument(byte[] xml,
            RcsSettings rcsSettings) throws PayloadException {
        try {
            FileTransferXmlParser ftHttpParser = new FileTransferXmlParser(xml, rcsSettings);
            ftHttpParser.parse();
            return ftHttpParser.getFileTransferInfo();

        } catch (ParserConfigurationException | ParseFailureException | SAXException e) {
            throw new PayloadException("Can't parse FT HTTP document!", e);
        }
    }

    /**
     * Get the HTTP file transfer info document
     * 
     * @param request Request
     * @param rcsSettings RCS settings
     * @return FT HTTP info
     * @throws PayloadException
     */
    public static FileTransferHttpInfoDocument getHttpFTInfo(SipRequest request,
            RcsSettings rcsSettings) throws PayloadException {
        /* Not a valid timestamp here as the message is just for temp use */
        long timestamp = -1;
        ChatMessage message = ChatUtils.getFirstMessage(request, timestamp);
        if (message == null || !FileTransferUtils.isFileTransferHttpType(message.getMimeType())) {
            return null;
        }
        return parseFileTransferHttpDocument(message.getContent().getBytes(UTF8), rcsSettings);
    }

    /**
     * Create a content object from URI
     * 
     * @param uri Uri of file
     * @param mimeType the mime type
     * @param disposition File disposition
     * @return Content instance
     */
    public static MmContent createMmContent(Uri uri, String mimeType, Disposition disposition) {
        final FileDescription desc = FileFactory.getFactory().getFileDescription(uri);
        MmContent content = ContentManager.createMmContent(uri, mimeType, desc.getSize(),
                desc.getName());
        if (Disposition.RENDER == disposition) {
            content.setPlayable(true);
        }
        return content;
    }

    /**
     * Create a content object for icon
     *
     * @param uri Uri of file
     * @return Content instance
     */
    public static MmContent createIconContent(Uri uri) {
        final FileDescription desc = FileFactory.getFactory().getFileDescription(uri);
        String mime = FileUtils.getMimeTypeFromExtension(uri.getPath());
        return ContentManager.createMmContent(uri, mime, desc.getSize(), desc.getName());
    }

    private static String getInfo(String fileType, Uri downloadUri, String name, String mimeType,
            long size, long expiration, String disposition, int playingLength) {
        StringBuilder info = new StringBuilder("<file-info type=\"").append(fileType).append("\"");
        if (disposition != null) {
            info.append(" file-disposition=\"").append(disposition).append("\"");
        }
        info.append(">");
        if (size != 0) {
            info.append("<file-size>").append(size).append("</file-size>");
        }
        if (name != null) {
            info.append("<file-name>").append(name).append("</file-name>");
        }
        if (mimeType != null) {
            info.append("<content-type>").append(mimeType).append("</content-type>");
        }
        if (playingLength != -1) {
            info.append("<am:playing-length>").append(playingLength).append("</am:playing-length>");
        }
        info.append("<data url = \"").append(downloadUri.toString()).append("\"  until=\"")
                .append(expiration).append("\"/></file-info>");
        return info.toString();
    }

    /**
     * Create HTTP file transfer info xml
     * 
     * @param fileTransferData File transfer info
     * @return String
     */
    public static String createHttpFileTransferXml(FileTransferHttpInfoDocument fileTransferData) {
        FileTransferHttpThumbnail fileIcon = fileTransferData.getFileThumbnail();
        StringBuilder info = new StringBuilder("<?xml version=\"1.0\" encoding=\"")
                .append(UTF8_STR).append("\"?><file>");
        if (fileIcon != null) {
            String fileIconInfo = getInfo(FILEICON_INFO, fileIcon.getUri(), null,
                    fileIcon.getMimeType(), fileIcon.getSize(), fileIcon.getExpiration(), null, -1);
            info.append(fileIconInfo);
        }
        String fileInfo = getInfo(FILE_INFO, fileTransferData.getUri(),
                fileTransferData.getFilename(), fileTransferData.getMimeType(),
                fileTransferData.getSize(), fileTransferData.getExpiration(),
                fileTransferData.getFileDisposition(), fileTransferData.getPlayingLength());
        info.append(fileInfo);
        info.append("</file>");
        return info.toString();
    }
}
