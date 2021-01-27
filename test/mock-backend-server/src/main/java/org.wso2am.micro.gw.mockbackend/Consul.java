package org.wso2am.micro.gw.mockbackend;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

interface ConsulState {
    void loadState(MockConsulServer server);
}

class ConsulNode {
    private String nodeId;
    private String nodeName;
    private String address;

    public ConsulNode(String nodeId, String nodeName, String address) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }
}

class ConsulTestCases {
    public static Map<String, ConsulState> testCases = new HashMap<>();

    public ConsulTestCases(){
        loadTestCases();
    }

    public static void loadTestCases() {
        testCases.put("1", consulServer -> {
            Datacenter localDc = new Datacenter("local-dc");
            Datacenter dc1 = new Datacenter("dc1");
            ConsulNode cn = new ConsulNode("d674a962-2e8e-d765-aa91-6d4d66fecacb", "machine", "127.0.0.1");
            ConsulNode cn1 = new ConsulNode("d675a962-278e-d865-aa61-6d4d66feaabc", "machine2", "192.168.43.1");
            Service webService = new Service("web", "");
            Service pizzaService = new Service("pizza", "");

            HealthCheck h = new HealthCheck();
            String[] tags = {"production"};
            Node n = new Node(Helper.generateRandomChars(), localDc, webService, "127.0.0.1", 3000, tags, h, cn);
            consulServer.addNode(n);

            //another dc
            HealthCheck h1 = new HealthCheck();
            String[] tags1 = {"production"};
            Node n1 = new Node(Helper.generateRandomChars(), dc1, webService, "127.0.0.1", 8080, tags1, h1, cn1);
            consulServer.addNode(n1);

            //another service
            HealthCheck h2 = new HealthCheck();
            String[] tags2 = {"production"};
            Node n2 = new Node(Helper.generateRandomChars(), localDc, pizzaService, "127.0.0.1", 5000, tags2, h2, cn);
            consulServer.addNode(n2);

            //another tag
            HealthCheck h3 = new HealthCheck();
            String[] tags3 = {"dev"};
            Node n3 = new Node(Helper.generateRandomChars(), localDc, webService, "127.0.0.1", 6000, tags3, h3, cn1);
            consulServer.addNode(n3);

            //health check critical
            HealthCheck h4 = new HealthCheck();
            h4.SetCritical();
            String[] tags4 = {"production"};
            Node n4 = new Node(Helper.generateRandomChars(), localDc, webService, "127.0.0.1", 7000, tags4, h4, cn);
            consulServer.addNode(n4);

            HealthCheck h5 = new HealthCheck();
            String[] tags5 = {"production"};
            Node n5 = new Node(Helper.generateRandomChars(), localDc, webService, "127.0.0.1", 4000, tags5, h5, cn1);
            consulServer.addNode(n5);

        });

        testCases.put("2", consulServer -> {
            Datacenter localDc = new Datacenter("local-dc");
            Datacenter dc1 = new Datacenter("dc1");
            ConsulNode cn = new ConsulNode("d674a962-2e8e-d765-aa91-6d4d66fecacb", "machine", "127.0.0.1");
            ConsulNode cn1 = new ConsulNode("d675a962-278e-d865-aa61-6d4d66feaabc", "machine2", "192.168.43.1");
            Service webService = new Service("web", "");

            HealthCheck h = new HealthCheck();
            String[] tags = {"production"};
            Node n = new Node(Helper.generateRandomChars(), localDc, webService, "127.0.0.1", 3000, tags, h, cn);
            consulServer.addNode(n);

            //another dc
            HealthCheck h1 = new HealthCheck();
            String[] tags1 = {"production"};
            Node n1 = new Node(Helper.generateRandomChars(), dc1, webService, "127.0.0.1", 8080, tags1, h1, cn1);
            consulServer.addNode(n1);

            //health check critical->passed
            HealthCheck h4 = new HealthCheck();
            String[] tags4 = {"production"};
            Node n4 = new Node(Helper.generateRandomChars(), localDc, webService, "127.0.0.1", 7000, tags4, h4, cn);
            consulServer.addNode(n4);

            //remove an old node, introduce a new node
            HealthCheck h5 = new HealthCheck();
            String[] tags5 = {"production"};
            Node n5 = new Node(Helper.generateRandomChars(), localDc, webService, "127.0.0.1", 4500, tags5, h5, cn1);
            consulServer.addNode(n5);
        });

        testCases.put("3", consulServer -> {
            consulServer.stopServer();
        });
    }
}

class Helper {
    public static String generateRandomChars() {
        String candidateChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int length = 5;
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(candidateChars.charAt(random.nextInt(candidateChars.length())));
        }
        return sb.toString();
    }
}

class Node {
    private String id;
    private Datacenter datacenter;
    private Service service;
    private String address;
    private int port;
    private String[] tags;
    private HealthCheck healthCheck;
    private ConsulNode consulNode;

    public Node(String id, Datacenter datacenter, Service service, String address, int port, String[] tags, HealthCheck healthCheck, ConsulNode consulNode) {
        this.id = id;
        this.datacenter = datacenter;
        this.service = service;
        this.address = address;
        this.port = port;
        this.tags = tags;
        this.healthCheck = healthCheck;
        this.consulNode = consulNode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ConsulNode getConsulNode() {
        return consulNode;
    }

    public HealthCheck getHealthCheck() {
        return healthCheck;
    }

    public Datacenter getDatacenter() {
        return datacenter;
    }

    public Service getService() {
        return service;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    @Override
    public String toString() {
        return "Node{" +
                "datacenter=" + datacenter +
                ", service=" + service +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", tags=" + Arrays.toString(tags) +
                ", healthCheck=" + healthCheck +
                '}';
    }
}


class Service {

    private String name;
    private String nameSpace;

    public Service(String name, String nameSpace) {
        this.name = name;
        this.nameSpace = nameSpace;

    }

    public Service(String name) {
        this.name = name;
        this.nameSpace = "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    @Override
    public String toString() {
        return "Service{" +
                "name='" + name + '\'' +
                ", nameSpace='" + nameSpace + '\'' +
                '}';
    }
}


class HealthCheck {
    private String status;

    public HealthCheck() {
        this.status = "passing";
    }

    public String getStatus() {
        return status;
    }

    public boolean isPassing() {
        return status.equals("passing");
    }

    public void SetCritical() {
        this.status = "critical";
    }

    @Override
    public String toString() {
        return "HealthCheck{" +
                "status='" + status + '\'' +
                '}';
    }
}

class Datacenter {
    private String name;

    public Datacenter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Datacenter{" +
                "name='" + name + '\'' +
                '}';
    }
}
