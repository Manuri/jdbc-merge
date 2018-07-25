/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.ballerinalang.mime.util;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.ballerinalang.bre.Context;
import org.ballerinalang.model.types.BStructureType;
import org.ballerinalang.model.util.StringUtils;
import org.ballerinalang.model.util.XMLUtils;
import org.ballerinalang.model.values.BJSON;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BRefValueArray;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.model.values.BXML;
import org.ballerinalang.runtime.message.BlobDataSource;
import org.ballerinalang.runtime.message.MessageDataSource;
import org.ballerinalang.runtime.message.StringDataSource;
import org.ballerinalang.stdlib.io.channels.TempFileIOChannel;
import org.ballerinalang.stdlib.io.channels.base.Channel;
import org.ballerinalang.stdlib.io.utils.BallerinaIOException;
import org.ballerinalang.util.exceptions.BallerinaException;
import org.jvnet.mimepull.MIMEPart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.ballerinalang.mime.util.MimeConstants.BODY_PARTS;
import static org.ballerinalang.mime.util.MimeConstants.CHARSET;
import static org.ballerinalang.mime.util.MimeConstants.ENTITY_BYTE_CHANNEL;
import static org.ballerinalang.mime.util.MimeConstants.FIRST_BODY_PART_INDEX;
import static org.ballerinalang.mime.util.MimeConstants.MESSAGE_DATA_SOURCE;
import static org.ballerinalang.mime.util.MimeConstants.MULTIPART_AS_PRIMARY_TYPE;

/**
 * Entity body related operations are included here.
 *
 * @since 0.963.0
 */
public class EntityBodyHandler {

    /**
     * Get a byte channel for a given text dataExpr.
     *
     * @param textPayload Text dataExpr that needs to be wrapped in a byte channel
     * @return EntityBodyChannel which represent the given text
     */
    public static EntityWrapper getEntityWrapper(String textPayload) {
        return new EntityWrapper(new EntityBodyChannel(new ByteArrayInputStream(
                textPayload.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * Given a temp file location, create a byte channel.
     *
     * @param temporaryFilePath Temporary file path
     * @return ByteChannel which represent the file channel
     */
    public static TempFileIOChannel getByteChannelForTempFile(String temporaryFilePath) {
        FileChannel fileChannel;
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        Path path = Paths.get(temporaryFilePath);
        try {
            fileChannel = (FileChannel) Files.newByteChannel(path, options);
        } catch (IOException e) {
            throw new BallerinaException("Error occurred while creating a file channel from a temporary file");
        }
        return new TempFileIOChannel(fileChannel, temporaryFilePath);
    }

    /**
     * Get the message dataExpr source associated with a given entity.
     *
     * @param entityStruct Represent a ballerina entity
     * @return MessageDataSource which represent the entity body in memory
     */
    public static MessageDataSource getMessageDataSource(BMap<String, BValue> entityStruct) {
        return entityStruct.getNativeData(MESSAGE_DATA_SOURCE) != null ? (MessageDataSource) entityStruct.getNativeData
                (MESSAGE_DATA_SOURCE) : null;
    }

    /**
     * Associate a given message dataExpr source with a given entity.
     *
     * @param entityStruct      Represent the ballerina entity
     * @param messageDataSource which represent the entity body in memory
     */
    public static void addMessageDataSource(BMap<String, BValue> entityStruct, MessageDataSource messageDataSource) {
        entityStruct.addNativeData(MESSAGE_DATA_SOURCE, messageDataSource);
    }

    /**
     * Construct BlobDataSource from the underneath byte channel which is associated with the entity struct.
     *
     * @param entityStruct Represent an entity struct
     * @return BlobDataSource Data source for binary dataExpr which is kept in memory
     * @throws IOException In case an error occurred while creating blob dataExpr source
     */
    public static BlobDataSource constructBlobDataSource(BMap<String, BValue> entityStruct) throws IOException {
        Channel byteChannel = getByteChannel(entityStruct);
        if (byteChannel == null) {
            return null;
        }
        byte[] byteData = MimeUtil.getByteArray(byteChannel.getInputStream());
        byteChannel.close();
        return new BlobDataSource(byteData);
    }

    /**
     * Construct JsonDataSource from the underneath byte channel which is associated with the entity struct.
     *
     * @param entityStruct Represent an entity struct
     * @return BJSON dataExpr source which is kept in memory
     */
    public static BJSON constructJsonDataSource(BMap<String, BValue> entityStruct) {
        try {
            BJSON jsonData;
            Channel byteChannel = getByteChannel(entityStruct);
            if (byteChannel == null) {
                return null;
            }
            String contentTypeValue = HeaderUtil.getHeaderValue(entityStruct, HttpHeaderNames.CONTENT_TYPE.toString());
            if (contentTypeValue != null && !contentTypeValue.isEmpty()) {
                String charsetValue = MimeUtil.getContentTypeParamValue(contentTypeValue, CHARSET);
                if (charsetValue != null && !charsetValue.isEmpty()) {
                    jsonData = new BJSON(byteChannel.getInputStream(), null, charsetValue);
                } else {
                    jsonData = new BJSON(byteChannel.getInputStream());
                }
            } else {
                jsonData = new BJSON(byteChannel.getInputStream());
            }
            byteChannel.close();
            return jsonData;
        } catch (IOException e) {
            throw new BallerinaIOException("Error occurred while closing connection", e);
        }
    }

    /**
     * Construct XMl dataExpr source from the underneath byte channel which is associated with the entity struct.
     *
     * @param entityStruct Represent an entity struct
     * @return BXML dataExpr source which is kept in memory
     */
    public static BXML constructXmlDataSource(BMap<String, BValue> entityStruct) {
        try {
            BXML xmlContent;
            Channel byteChannel = getByteChannel(entityStruct);
            if (byteChannel == null) {
                throw new BallerinaIOException("Empty xml payload");
            }
            String contentTypeValue = HeaderUtil.getHeaderValue(entityStruct, HttpHeaderNames.CONTENT_TYPE.toString());
            if (contentTypeValue != null && !contentTypeValue.isEmpty()) {
                String charsetValue = MimeUtil.getContentTypeParamValue(contentTypeValue, CHARSET);
                if (charsetValue != null && !charsetValue.isEmpty()) {
                    xmlContent = XMLUtils.parse(byteChannel.getInputStream(), charsetValue);
                } else {
                    xmlContent = XMLUtils.parse(byteChannel.getInputStream());
                }
            } else {
                xmlContent = XMLUtils.parse(byteChannel.getInputStream());
            }
            byteChannel.close();
            return xmlContent;
        } catch (IOException e) {
            throw new BallerinaIOException("Error occurred while closing the channel", e);
        }
    }

    /**
     * Construct StringDataSource from the underneath byte channel which is associated with the entity struct.
     *
     * @param entityStruct Represent an entity struct
     * @return StringDataSource which represent the entity body which is kept in memory
     */
    public static StringDataSource constructStringDataSource(BMap<String, BValue> entityStruct) {
        try {
            String textContent;
            Channel byteChannel = getByteChannel(entityStruct);
            if (byteChannel == null) {
                throw new BallerinaIOException("String payload is null");
            }
            String contentTypeValue = HeaderUtil.getHeaderValue(entityStruct, HttpHeaderNames.CONTENT_TYPE.toString());
            if (contentTypeValue != null && !contentTypeValue.isEmpty()) {
                String charsetValue = MimeUtil.getContentTypeParamValue(contentTypeValue, CHARSET);
                if (charsetValue != null && !charsetValue.isEmpty()) {
                    textContent = StringUtils.getStringFromInputStream(byteChannel.getInputStream(), charsetValue);
                } else {
                    textContent = StringUtils.getStringFromInputStream(byteChannel.getInputStream());
                }
            } else {
                textContent = StringUtils.getStringFromInputStream(byteChannel.getInputStream());
            }
            byteChannel.close();
            return new StringDataSource(textContent);
        } catch (IOException e) {
            throw new BallerinaIOException("Error occurred while closing the channel", e);
        }
    }

    /**
     * Check whether the entity body is present. Entity body can either be a byte channel, fully constructed
     * message dataExpr source or a set of body parts.
     *
     * @param entityStruct Represent an 'Entity'
     * @return a boolean indicating entity body availability
     */
    public static boolean checkEntityBodyAvailability(BMap<String, BValue> entityStruct) {
        return entityStruct.getNativeData(ENTITY_BYTE_CHANNEL) != null || getMessageDataSource(entityStruct) != null
                || entityStruct.getNativeData(BODY_PARTS) != null;
    }

    /**
     * Set ballerina body parts to it's top level entity.
     *
     * @param entity    Represent top level message's entity
     * @param bodyParts Represent ballerina body parts
     */
    static void setPartsToTopLevelEntity(BMap<String, BValue> entity, ArrayList<BMap<String, BValue>> bodyParts) {
        if (!bodyParts.isEmpty()) {
            BStructureType typeOfBodyPart = (BStructureType) bodyParts.get(FIRST_BODY_PART_INDEX).getType();
            BMap<String, BValue>[] result = bodyParts.toArray(new BMap[bodyParts.size()]);
            BRefValueArray partsArray = new BRefValueArray(result, typeOfBodyPart);
            entity.addNativeData(BODY_PARTS, partsArray);
        }
    }

    /**
     * Populate ballerina body parts with actual body content. Based on the memory threshhold body part's inputstream
     * can either come from memory or from a temp file maintained by mimepull library.
     *
     * @param bodyPart Represent ballerina body part
     * @param mimePart Represent decoded mime part
     */
    public static void populateBodyContent(BMap<String, BValue> bodyPart, MIMEPart mimePart) {
        bodyPart.addNativeData(ENTITY_BYTE_CHANNEL, new MimeEntityWrapper(new EntityBodyChannel(mimePart.readOnce()),
                mimePart));
    }

    /**
     * Write byte channel stream directly into outputstream without converting it to a dataExpr source.
     *
     * @param entityStruct        Represent a ballerina entity
     * @param messageOutputStream Represent the outputstream that the message should be written to
     * @throws IOException When an error occurs while writing inputstream to outputstream
     */
    public static void writeByteChannelToOutputStream(BMap<String, BValue> entityStruct,
                                                      OutputStream messageOutputStream)
            throws IOException {
        Channel byteChannel = EntityBodyHandler.getByteChannel(entityStruct);
        if (byteChannel != null) {
            MimeUtil.writeInputToOutputStream(byteChannel.getInputStream(), messageOutputStream);
            byteChannel.close();
            //Set the byte channel to null, once it is consumed
            entityStruct.addNativeData(ENTITY_BYTE_CHANNEL, null);
        }
    }

    /**
     * Decode a given entity body to get a set of child parts and set them to parent entity's multipart dataExpr field.
     *
     * @param context      Represent the ballerina context
     * @param entityStruct Parent entity that the nested parts reside
     * @param byteChannel  Represent ballerina specific byte channel
     */
    public static void decodeEntityBody(Context context, BMap<String, BValue> entityStruct, Channel byteChannel) {
        String contentType = MimeUtil.getContentTypeWithParameters(entityStruct);
        if (!MimeUtil.isNotNullAndEmpty(contentType) || !contentType.startsWith(MULTIPART_AS_PRIMARY_TYPE)) {
            return;
        }

        MultipartDecoder.parseBody(context, entityStruct, contentType, byteChannel.getInputStream());
    }

    /**
     * Extract body parts from a given entity.
     *
     * @param entityStruct Represent a ballerina entity
     * @return An array of body parts
     */
    public static BRefValueArray getBodyPartArray(BMap<String, BValue> entityStruct) {
        return entityStruct.getNativeData(BODY_PARTS) != null ?
                (BRefValueArray) entityStruct.getNativeData(BODY_PARTS) : new BRefValueArray();
    }

    public static Channel getByteChannel(BMap<String, BValue> entityStruct) {
        return entityStruct.getNativeData(ENTITY_BYTE_CHANNEL) != null ? (Channel) entityStruct.getNativeData
                (ENTITY_BYTE_CHANNEL) : null;
    }
}
