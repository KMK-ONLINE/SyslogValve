package org.apache.catalina.valves;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.MessageLengthLimitException;
import org.apache.catalina.ValveParserConfigurationException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class UdpJSONAccessLogValve extends AbstractAccessLogValve {

  private static final Log LOG = LogFactory.getLog(UdpJSONAccessLogValve.class);

  private static final String DEFAULT_JSON_FORMAT_PATTERN = "{"
      + "\"@timestamp\":\"%{yyyy-MM-dd'T'HH:mm:ss.SSSZ}t\","
      + "\"type\":\"tomcataccess\","
      + "\"agent\":\"%{User-Agent}i\","
      + "\"auth\":\"%u\","
      + "\"bytes\":%B,"
      + "\"clientip\":\"%h\","
      + "\"httpversion\":\"%H\","
      + "\"ident\":\"%l\","
      + "\"request\":\"%U%q\","
      + "\"request_time\":%D,"
      + "\"response\":%s,"
      + "\"timestamp\":\"%{dd/MMM/yyyy:HH:mm:ss Z}t\","
      + "\"verb\":\"%m\","
      + "\"vhost\":\"%v\","
      + "\"x_forwarded_for\":\"%{X-Forwarded-For}i\""
      + "}";

  //------------------------------------------------------ Constructor
  public UdpJSONAccessLogValve() {
    super();
  }

  // ----------------------------------------------------- Instance Variables

  private boolean forUnitTest = false;

  private DatagramSocket datagramSocket;
  private InetAddress hostnameInetAddress;
  private int intPort;
  private int intMessageLengthLimit;

  private String hostname;
  private String port;

  // XXX: adapted from fluentd message_length_limit: 32766
  private String messageLengthLimit = "32766";

  // ----------------------------------------------------- Getters/Setters
  protected void setForUnitTest(boolean forUnitTest) {
    this.forUnitTest = forUnitTest;
  }

  // ----------------------------------------------------- Properties

  /**
   * Return the hostname in which we send the logs to.
   */
  public String getHostname() {
    return hostname;
  }

  /**
   * Set the hostname in which we send the logs to.
   *
   * @param hostname The new hostname
   */
  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  /**
   * Return the port of hostname in which we send the logs to.
   */
  public String getPort() {
    return port;
  }

  /**
   * Set the port of hostname in which we send the logs to.
   *
   * @param port The new port of hostname
   */
  public void setPort(String port) {
    this.port = port;
  }

  /**
   * Return the message length limit of the log.
   */
  public String getMessageLengthLimit() {
    return messageLengthLimit;
  }

  /**
   * Set the message length limit of the log.
   *
   * @param messageLengthLimit The new port of hostname
   */
  public void setMessageLengthLimit(String messageLengthLimit) {
    this.messageLengthLimit = messageLengthLimit;
  }

  //------------------------------------------------------ Overrides

  @Override
  protected void log(CharArrayWriter charArrayWriter) {
    try {
      byte[] logJSONBytes = convertToJSONBytes(charArrayWriter);

      if (logJSONBytes.length > this.intMessageLengthLimit) {
        final MessageLengthLimitException messageLengthLimitException = new MessageLengthLimitException(
            logJSONBytes.length, this.intMessageLengthLimit);
        LOG.error("Unable to log entry", messageLengthLimitException);
        return;
      }

      DatagramPacket datagramPacket = new DatagramPacket(
          logJSONBytes,
          0,
          logJSONBytes.length,
          this.hostnameInetAddress,
          this.intPort
      );

      this.datagramSocket.send(datagramPacket);

    } catch (JSONException | IOException e) {
      LOG.error("Failed to log entry", e);
    }
  }

  /**
   * Start this component and implement the requirements of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
   *
   * @throws LifecycleException if this component detects a fatal error that prevents this component
   * from being used
   */
  @Override
  protected synchronized void startInternal() throws LifecycleException {
    try {
      configurePattern();
      parseIntConfigurations();

      this.hostnameInetAddress = InetAddress.getByName(this.hostname);
      this.datagramSocket = new DatagramSocket();
    } catch (SocketException | UnknownHostException | ValveParserConfigurationException e) {
      throw new LifecycleException(e);
    }

    LOG.info(
        new StringBuilder().
            append("Starting component with valve params... hostname: ").append(this.hostname).
            append(", port: ").append(this.port).
            append(", pattern: ").append(this.getPattern()).
            append(", messageLengthLimit: ").append(this.messageLengthLimit).toString()
    );
    if (!this.forUnitTest) {
      super.startInternal();
    }
    LOG.info("Component started!");
  }

  /**
   * Stop this component and implement the requirements of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
   *
   * @throws LifecycleException if this component detects a fatal error that prevents this component
   * from being used
   */
  @Override
  protected synchronized void stopInternal() throws LifecycleException {
    LOG.info("Shutting down component...");

    if (!this.forUnitTest) {
      super.stopInternal();
    }

    this.datagramSocket.disconnect();
    this.datagramSocket.close();

    LOG.info(
        new StringBuilder().
            append("Shutdown completed; isDatagramSocketConnected: ").
            append(this.datagramSocket.isConnected()).
            append(", isDatagramSocketClosed: ").
            append(this.datagramSocket.isClosed()).toString()
    );
  }

  //------------------------------------------------------ Protected
  protected void parseIntConfigurations() throws ValveParserConfigurationException {
    try {
      this.intPort = Integer.parseInt(this.port);
      this.intMessageLengthLimit = Integer.parseInt(this.messageLengthLimit);
    } catch (NumberFormatException e) {
      throw new ValveParserConfigurationException(e);
    }
  }

  //------------------------------------------------------ Private
  private void configurePattern() {
    String configuredPattern = this.getPattern();

    if (configuredPattern == null || configuredPattern == "") {
      this.setPattern(DEFAULT_JSON_FORMAT_PATTERN);
      return;
    }
  }

  private byte[] convertToJSONBytes(CharArrayWriter charArrayWriter) throws JSONException {
    String logString = charArrayWriter.toString();
    JSONObject logJSONObject = new JSONObject(logString);
    return logJSONObject.toString().getBytes();
  }
}
