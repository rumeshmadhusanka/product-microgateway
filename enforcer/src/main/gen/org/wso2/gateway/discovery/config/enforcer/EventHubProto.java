// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: wso2/discovery/config/enforcer/event_hub.proto

package org.wso2.gateway.discovery.config.enforcer;

public final class EventHubProto {
  private EventHubProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_discovery_config_enforcer_EventHub_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_discovery_config_enforcer_EventHub_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n.wso2/discovery/config/enforcer/event_h" +
      "ub.proto\022\031discovery.config.enforcer\"\212\001\n\010" +
      "EventHub\022\017\n\007enabled\030\001 \001(\010\022\022\n\nserviceUrl\030" +
      "\002 \001(\t\022\033\n\023internalDataContext\030\003 \001(\t\022\020\n\010us" +
      "ername\030\004 \001(\t\022\020\n\010password\030\005 \001(\t\022\030\n\020listen" +
      "erEndpoint\030\006 \001(\tB\215\001\n*org.wso2.gateway.di" +
      "scovery.config.enforcerB\rEventHubProtoP\001" +
      "ZNgithub.com/envoyproxy/go-control-plane" +
      "/wso2/discovery/config/enforcer;enforcer" +
      "b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_discovery_config_enforcer_EventHub_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_discovery_config_enforcer_EventHub_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_discovery_config_enforcer_EventHub_descriptor,
        new java.lang.String[] { "Enabled", "ServiceUrl", "InternalDataContext", "Username", "Password", "ListenerEndpoint", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
