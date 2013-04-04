/*
 * Copyright 2013 Jeanfrancois Arcand
 *
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
 */
package org.atmosphere.wasync.samples;

import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.impl.AtmosphereClient;
import org.codehaus.jackson.map.ObjectMapper;
import org.atmosphere.wasync.samples.Chat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

public class wAsyncChat {

    private final static Logger logger = LoggerFactory.getLogger(wAsyncChat.class);
    private final static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            args = new String[] {"http://127.0.0.1:8080"};
        }

        Options options = new Options.OptionsBuilder().build();
        AtmosphereClient client = ClientFactory.getDefault().newClient(AtmosphereClient.class);

        RequestBuilder request = client.newRequestBuilder()
                .method(Request.METHOD.GET)
                .uri(args[0] + "/chat")
                .trackMessageLength(true)
                .encoder(new Encoder<String, Chat.Data>() {
                    @Override
                    public Chat.Data encode(String s) {
                        try {
                            return mapper.readValue(s, Chat.Data.class);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .encoder(new Encoder<Chat.Data, String>() {
                    @Override
                    public String encode(Chat.Data data) {
                        try {
                            return mapper.writeValueAsString(data);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .decoder(new Decoder<String, Chat.Data>() {
                    @Override
                    public Chat.Data decode(Transport.EVENT_TYPE type, String data) {

                        data = data.trim();

                        // Padding
                        if (data.length() == 0) {
                            return null;
                        }

                        if (type.equals(Transport.EVENT_TYPE.MESSAGE)) {
                            try {
                                return mapper.readValue(data, Chat.Data.class);
                            } catch (IOException e) {
                                logger.debug("Invalid message {}", data);
                                return null;
                            }
                        } else {
                            return null;
                        }
                    }
                })
                .transport(Request.TRANSPORT.WEBSOCKET)
                .transport(Request.TRANSPORT.LONG_POLLING);

        Socket socket = client.create(options);
        socket.on("message", new Function<Chat.Data>() {
            @Override
            public void on(Chat.Data t) {
                Date d = new Date(t.getTime()) ;
                logger.info("Author {}: {}", t.getAuthor() + "@ " + d.getHours() + ":" + d.getMinutes(), t.getMessage());
            }
        }).on(new Function<Throwable>() {

            @Override
            public void on(Throwable t) {
                t.printStackTrace();
            }

        }).open(request.build());

        logger.info("Choose Name: ");
        String name = null;
        String a = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (!(a.equals("quit"))) {
            a = br.readLine();
            if (name == null) {
                name = a;
            }
            socket.fire(new Chat.Data(name, a));
        }
        socket.close();
    }


}

