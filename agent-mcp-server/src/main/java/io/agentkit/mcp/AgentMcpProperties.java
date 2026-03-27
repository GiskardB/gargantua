package io.agentkit.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Agent MCP Server.
 */
@ConfigurationProperties(prefix = "agent.mcp")
public class AgentMcpProperties {

    private boolean enabled = false;
    private Server server = new Server();
    private Transport transport = new Transport();
    private Gateway gateway = new Gateway();
    private String mode = "standalone";
    private Security security = new Security();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class Server {
        private String name = "agent-mcp-server";
        private String version = "1.0.0";
        private String description = "AI Agent MCP Server";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class Transport {
        private String type = "sse";
        private String path = "/mcp";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Gateway {
        private String toolName = "agent-chat";
        private String toolDescription = "Send a message to the AI agent for processing";

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getToolDescription() {
            return toolDescription;
        }

        public void setToolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
        }
    }

    public static class Security {
        private boolean authRequired = false;
        private String tokenHeader = "Authorization";

        public boolean isAuthRequired() {
            return authRequired;
        }

        public void setAuthRequired(boolean authRequired) {
            this.authRequired = authRequired;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }
    }
}
