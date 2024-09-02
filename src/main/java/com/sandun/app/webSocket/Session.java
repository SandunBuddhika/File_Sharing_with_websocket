package com.sandun.app.webSocket;

public class Session {
    private String key;
    private String hostid;
    private String clientId;
    private boolean state;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getHostid() {
        return hostid;
    }

    public void setHostid(String hostid) {
        this.hostid = hostid;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public boolean isState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }
}
