<?php
require_once 'vendor/autoload.php';

use Ratchet\MessageComponentInterface;
use Ratchet\ConnectionInterface;
use Ratchet\Server\IoServer;
use Ratchet\Http\HttpServer;
use Ratchet\WebSocket\WsServer;

class Chat implements MessageComponentInterface
{
    protected $clients;
    protected $sessions;

    public function __construct()
    {
        $this->clients = new \SplObjectStorage;
        $this->sessions = new stdClass();
    }

    public function onOpen(ConnectionInterface $conn)
    {
        $this->clients->attach($conn);
        $conn->send(json_encode(array("type" => "initial", "state" => "200", "data" => "Welcome to the WebSocket server!")));
    }

    public function onMessage(ConnectionInterface $from, $msg)
    {
        $data = json_decode($msg, true);
        $type = $data["type"];
        switch ($type) {
            case "REGISTER":
                $this->registerAClient($data, $from);
                break;
            case "CREATE_A_SESSION":
                //i should create a session pool so it's easy to read and write session when i needed
                $this->createAHost($from);
                break;
            case "JOIN_TO_SESSION":
                if (isset($data["sessionKey"])) {
                    $this->connectToAHost($from, $data["sessionKey"]);
                } else {
                    $from->send(json_encode(array("state" => "405", "type" => "JoinedAHost", "data" => "Unable to join to the session")));
                }
                break;
            case "FILE_SHARE_START_UP":
                $this->startUpSendingFile($from, $data);
                break;
            case "FILE_SHARE_START_UP_STATUS":
                $this->fileSharingRequestStatus($from, $data);
                break;
            case "SHARING":
                $this->shareTheFile($from, $data);
                break;
            case "FINISH_SHARING":
                $this->finishedSharing($from, $data);
                break;
        }
    }

    protected function registerAClient($data, $from)
    {
        if (isset($data["userId"]) && isset($data["username"])) {
            $from->userId = $data["userId"];
            $from->username = $data["username"];
            $from->send(json_encode(array("state" => "200", "type" => "registration", "data" => "Successfully registered!!")));
        }
    }


    protected function createAHost($hostClient)
    {
        $uId = uniqid();
        $session = new Session();
        $session->host = $hostClient;
        $session->state = true;
        $hostClient->isHosting = true;
        $hostClient->hostingSessionKey = $uId;
        $this->sessions->$uId = $session;
        $hostClient->send(json_encode(array("state" => "200", "type" => "HostCreated", "data" => "Successfully Host Create!!", "sessionKey" => $uId)));
    }

    protected function connectToAHost($client, $sessionKey)
    {
        foreach ($this->sessions as $key => $value) {
            if ($key == $sessionKey && $value->state) {
                $value->client = $client;
                $value->state = false;
                $value->host->send(json_encode(array("state" => "200", "type" => "JoinedClient", "data" => "Successfully client connected to the session!!", "clientId" => $client->userId)));
                $client->send(json_encode(array("state" => "200", "type" => "JoinedAHost", "data" => "Successfully connected to the session!!", "hostId" => $value->host->userId)));
            }
        }
    }
    protected function startUpSendingFile($from, $data)
    {
        //need to get file detailes and send it the receiver 
        //after that need to create a another method to verifiy the file receving user is active and ready to send receive data  
        $fileName = $data["fileName"];
        $fileSize = $data["fileSize"];
        $fileExtension = $data["fileExtension"];
        $sessionKey = $data["sessionKey"];
        $fromId = $from->userId;

        foreach ($this->sessions as $key => $value) {
            if ($key == $sessionKey && !$value->state) {
                if ($value->client->userId == $fromId) {
                    $value->host->send(json_encode(array("state" => "200", "type" => "fileStateStartUp", "fileName" => $fileName, "fileExtension" => $fileExtension, "fileSize" => $fileSize)));
                } else {
                    $value->client->send(json_encode(array("state" => "200", "type" => "fileStateStartUp", "fileName" => $fileName, "fileExtension" => $fileExtension, "fileSize" => $fileSize)));
                }
            }
        }
    }
    protected function fileSharingRequestStatus($from, $data)
    {
        $status = $data["status"];
        $sessionKey = $data["sessionKey"];
        $fromId = $from->userId;

        foreach ($this->sessions as $key => $value) {
            if ($key == $sessionKey && !$value->state) {
                if ($value->client->userId == $fromId) {
                    $value->host->send(json_encode(array("state" => "200", "type" => "fileShareStatus", "status" => $status)));
                } else {
                    $value->client->send(json_encode(array("state" => "200", "type" => "fileShareStatus", "status" => $status)));
                }
            }
        }
    }
    protected function shareTheFile($from, $data)
    {
        $fromId = $from->userId;
        $sessionKey = $data["sessionKey"];
        $fileData = $data["fileData"];
        foreach ($this->sessions as $key => $value) {
            if ($key == $sessionKey && !$value->state) {
                if ($value->client->userId == $fromId) {
                    $value->host->send(json_encode(array("state" => "200", "type" => "sentFileData", "fileData" => $fileData)));
                } else {
                    $value->client->send(json_encode(array("state" => "200", "type" => "sentFileData", "fileData" => $fileData)));
                }
            }
        }
    }
    protected function finishedSharing($from, $data)
    {
        $fromId = $from->userId;
        $sessionKey = $data["sessionKey"];
        $state = $data["status"];
        foreach ($this->sessions as $key => $value) {
            if ($key == $sessionKey && !$value->state) {
                if ($value->client->userId == $fromId) {
                    $value->host->send(json_encode(array("state" => "200", "type" => "fileSharingFinished", "status" => $state)));
                } else {
                    $value->client->send(json_encode(array("state" => "200", "type" => "fileSharingFinished", "status" => $state)));
                }
            }
        }
    }

    protected function closeASession($client)
    {
        $key = $client->hostingSessionKey;
        unset($this->sessions[$key]);
        $client->isHosting = false;
        $client->hostingSessionKey = "";
    }

    public function onClose(ConnectionInterface $conn)
    {
        $this->clients->detach($conn);
    }

    public function onError(ConnectionInterface $conn, \Exception $e)
    {
        $conn->close();
    }
}

$server = IoServer::factory(
    new HttpServer(
        new WsServer(
            new Chat()
        )
    ),
    8080
);

class Session
{
    public $host;
    public $client;
    public $state;
}

echo "WebSocket server started at port 8080\n";
$server->run();
