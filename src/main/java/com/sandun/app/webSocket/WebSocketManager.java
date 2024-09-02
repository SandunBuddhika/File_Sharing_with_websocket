package com.sandun.app.webSocket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import okio.ByteString;

import java.io.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

import java.util.concurrent.TimeUnit;

public class WebSocketManager {

    private WebSocket ws;
    private Listener listener;
    private OkHttpClient client;
    private String userId;
    private String username;
    private Session session;
    private boolean inASession;
    private boolean isAFileSharing;
    private File receivingFile;
    private FileOutputStream outputStream;
    private String receivingFileName;


    public WebSocketManager init(String userId, String username) {
        client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
        Request request = new Request.Builder()
                .url("ws://localhost:8080/ChatApplicationServerSide/fileShareTest.php")
                .build();
        this.listener = new Listener(this);
        this.ws = client.newWebSocket(request, listener);
        this.userId = userId;
        this.username = username;
        return this;
    }

    public void closeConnection() {
        client.dispatcher().executorService().shutdown();
    }

    private void register() {
        JsonObject json = new JsonObject();
        json.addProperty("userId", userId);
        json.addProperty("username", username);
        json.addProperty("type", RequestResponseType.REGISTER.toString());
        ws.send(json.toString());
    }

    public void createAHostSession() {
        if (!inASession) {
            JsonObject json = new JsonObject();
            json.addProperty("type", RequestResponseType.CREATE_A_SESSION.toString());
            ws.send(json.toString());
        }
    }

    public void connectToAHost(String key) {
        if (!inASession) {
            Session session = new Session();
            session.setKey(key);
            session.setClientId(userId);
            this.session = session;
            isAFileSharing = true;
            JsonObject json = new JsonObject();
            json.addProperty("sessionKey", key);
            json.addProperty("type", RequestResponseType.JOIN_TO_SESSION.toString());
            ws.send(json.toString());
        }
    }


    public void startSharing(File file) {
        if (inASession) {
            String name = file.getName().substring(0, file.getName().lastIndexOf("."));
            String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            String sizeText;
            double size = file.length();
            if (size <= 1024) {
                size = (double) file.length() / 1024;
                sizeText = size + " KB";
            } else if (size > 1024 * 1024) {
                size = (double) file.length() / (1024 * 1024);
                sizeText = size + " MB";
            } else {
                sizeText = size + " Bytes";
            }
            JsonObject json = new JsonObject();
            json.addProperty("sessionKey", session.getKey());
            json.addProperty("fileName", name);
            json.addProperty("fileSize", sizeText);
            json.addProperty("type", RequestResponseType.FILE_SHARE_START_UP.toString());
            json.addProperty("fileExtension", extension);
            ws.send(json.toString());
        } else {
            System.out.println("Please wait till someone connected to the session...");
        }
    }

    public void initFileReceiving() {
        try {
            receivingFile = new File("src/main/resources/R" + receivingFileName);
            outputStream = new FileOutputStream(receivingFile);
        } catch (IOException e) {
            receivingFile = null;
            e.printStackTrace();
        }
    }

    public void isReceivingFIle(boolean status) {
        isAFileSharing = true;
        if (status) {
            initFileReceiving();
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("type", RequestResponseType.FILE_SHARE_START_UP_STATUS.toString());
        obj.addProperty("status", status ? "READY" : "NOT READY");
        obj.addProperty("sessionKey", session.getKey());
        ws.send(obj.toString());
    }

    public void sharing(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            new Thread(() -> {
                try {
                    byte[] buf = new byte[500000];
                    while (fis.read(buf) != -1) {
                        String encodedFileChunk = Base64.getEncoder().encodeToString(buf);
                        JsonObject json = new JsonObject();
                        json.addProperty("sessionKey", session.getKey());
                        json.addProperty("type", RequestResponseType.SHARING.toString());
                        json.addProperty("fileData", encodedFileChunk);
                        ws.send(json.toString());
                        Thread.sleep(10);
                    }
                    fis.close();
                    JsonObject finishReq = new JsonObject();
                    finishReq.addProperty("sessionKey", session.getKey());
                    finishReq.addProperty("type", RequestResponseType.FINISH_SHARING.toString());
                    finishReq.addProperty("status", "DONE");
                    ws.send(finishReq.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
            //need to handle the error (stop sharing and show an error to the receiver)
        }

    }

    public void receiving(String data) {
        if (receivingFile != null) {
            try {
                byte[] decodedFileChunk = Base64.getDecoder().decode(data);
                outputStream.write(decodedFileChunk);
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("ERROR file receiving");
        }
    }

    public void finishSharing() {
        try {
            outputStream.close();
            receivingFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Listener extends WebSocketListener {
        private WebSocketManager manager;

        public Listener(WebSocketManager manager) {
            this.manager = manager;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            System.out.println("Opened webSocket!!");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            System.err.println("output" + text);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            String statesCode = json.get("state").getAsString();
            switch (json.get("type").getAsString()) {
                case "initial":
                    if (statesCode.equals("200")) {
                        System.out.println("Connected!");
                        manager.register();
                    } else {
                        System.out.println("Error");
                    }
                    break;
                case "HostCreated":
                    if (statesCode.equals("200") && json.get("sessionKey").getAsString() != null) {
                        Session session = new Session();
                        session.setKey(json.get("sessionKey").getAsString());
                        System.out.println(json.get("data").getAsString());
                        manager.session = session;
                        manager.inASession = true;
                    } else {
                        System.out.println("Error");
                    }
                    break;
                case "JoinedAHost":
                    if (statesCode.equals("200")) {
                        if (manager.session != null) {
                            manager.session.setHostid(json.get("hostId").getAsString());
                            manager.session.setState(true);
                            System.out.println(json.get("data").getAsString());
                            manager.inASession = true;
                        }
                    } else {
                        System.out.println("Error");
                    }
                    break;
                case "JoinedClient":
                    if (statesCode.equals("200")) {
                        if (manager.session != null) {
                            manager.session.setClientId(json.get("clientId").getAsString());
                            manager.session.setState(true);
                            System.out.println(json.get("data").getAsString());
                            manager.inASession = true;
                        } else {
                            //request to close the session...
                        }
                    } else {
                        System.out.println("Error");
                    }
                    break;
                case "fileStateStartUp":
                    //show data in the ui this not applicable in here
                    manager.receivingFileName = json.get("fileName").getAsString() + "." + json.get("fileExtension").getAsString();
                    break;
                case "sentFileData":
                    if (statesCode.equals("200")) {
                        if (manager.session != null) {
                            manager.receiving(json.get("fileData").getAsString());
                        } else {
                            //request to close the session...
                        }
                    } else {
                        System.out.println("ERROR");
                    }
                    break;
                case "fileSharingFinished":
                    if (statesCode.equals("200")) {
                        if (manager.session != null) {
                            if (json.get("status").getAsString().equals("DONE")) {
                                manager.finishSharing();
                            } else {
                                manager.receivingFile = null;
                                manager.outputStream = null;
                                System.out.println("File Sharing Stopped...");
                            }
                        } else {
                            //request to close the session...
                        }
                    } else {
                        System.out.println("ERROR");
                    }
                    break;
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            System.out.println("Receiving: " + bytes.hex());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, null);
            System.out.println("Closing: " + code + " " + reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            t.printStackTrace();
        }

    }
}

