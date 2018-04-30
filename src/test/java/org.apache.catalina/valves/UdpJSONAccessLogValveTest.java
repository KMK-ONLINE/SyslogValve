package org.apache.catalina.valves;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.ValveParserConfigurationException;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UdpJSONAccessLogValveTest {

  private UdpJSONAccessLogValve udpJSONAccessLogValve;
  private DatagramServerThread datagramServerThread;

  @Before
  public void setUp() throws IOException, LifecycleException {
    datagramServerThread = new DatagramServerThread();

    udpJSONAccessLogValve = new UdpJSONAccessLogValve();
    udpJSONAccessLogValve.setHostname(datagramServerThread.getHostname());
    udpJSONAccessLogValve.setPort(datagramServerThread.getPort());

    udpJSONAccessLogValve.setForUnitTest(true);
    udpJSONAccessLogValve.startInternal();

    datagramServerThread.start();
  }

  @After
  public void tearDown() throws LifecycleException, InterruptedException {
    udpJSONAccessLogValve.stopInternal();

    datagramServerThread.join();
  }

  @Test
  public void shouldLogSuccessfully() throws InterruptedException {
    // Given
    final CharArrayWriter charArrayWriter = new CharArrayWriter();
    charArrayWriter.append(
        "{\"request\":\"/?pretty=wow\",\"agent\":\"curl/7.47.0\",\"ident\":\"-\",\"verb\":\"GET\",\"type\":\"tomcataccess\",\"x_forwarded_for\":\"-\",\"request_time\":4,\"@timestamp\":\"2018-04-27T07:09:00.982+0000\",\"bytes\":11250,\"response\":200,\"clientip\":\"172.17.0.1\",\"httpversion\":\"HTTP/1.1\",\"timestamp\":\"27/Apr/2018:07:09:00 +0000\",\"vhost\":\"localhost\"}");

    // When
    udpJSONAccessLogValve.log(charArrayWriter);
    datagramServerThread.join();

    // Then
    Assert.assertThat(datagramServerThread.getReceiveIOException(),
        Matchers.nullValue(IOException.class));
    Assert.assertThat(datagramServerThread.getReceivedString(), Matchers
        .is("{\"request\":\"\\/?pretty=wow\",\"agent\":\"curl\\/7.47.0\",\"ident\":\"-\",\"verb\":\"GET\",\"type\":\"tomcataccess\",\"x_forwarded_for\":\"-\",\"request_time\":4,\"@timestamp\":\"2018-04-27T07:09:00.982+0000\",\"bytes\":11250,\"response\":200,\"clientip\":\"172.17.0.1\",\"httpversion\":\"HTTP\\/1.1\",\"timestamp\":\"27\\/Apr\\/2018:07:09:00 +0000\",\"vhost\":\"localhost\"}"));
  }

  @Test
  public void shouldNotLogOnNonJSONPayload() throws InterruptedException, SocketException {
    // Given
    final CharArrayWriter charArrayWriter = new CharArrayWriter();
    charArrayWriter.append("not-a-json-doc");

    // XXX: shortens wait time on join as it is expected to receive nothing
    datagramServerThread.setSoTimeout(500);

    // When
    udpJSONAccessLogValve.log(charArrayWriter);
    datagramServerThread.join();

    // Then

    // XXX: asserts that server never receives any data
    final IOException receiveIOException = datagramServerThread.getReceiveIOException();
    Assert.assertThat(receiveIOException, Matchers.notNullValue());
    Assert.assertThat(receiveIOException, IsInstanceOf.instanceOf(SocketTimeoutException.class));
    Assert.assertThat(receiveIOException.getMessage(), Matchers.is("Receive timed out"));

    Assert.assertThat(datagramServerThread.getReceivedString(), Matchers.nullValue(String.class));
  }

  @Test
  public void shouldNotLogWhenMessageLengthLimitIsExceeded()
      throws LifecycleException, SocketException, InterruptedException, ValveParserConfigurationException {
    // Given
    udpJSONAccessLogValve.setMessageLengthLimit("1");
    udpJSONAccessLogValve.parseIntConfigurations();

    final CharArrayWriter charArrayWriter = new CharArrayWriter();
    charArrayWriter.append("{\"name\":\"John\",\"age\":25}");

    // XXX: shortens wait time on join as it is expected to receive nothing
    datagramServerThread.setSoTimeout(500);

    // When
    udpJSONAccessLogValve.log(charArrayWriter);
    datagramServerThread.join();

    // Then

    // XXX: asserts that server never receives any data
    final IOException receiveIOException = datagramServerThread.getReceiveIOException();
    Assert.assertThat(receiveIOException, Matchers.notNullValue());
    Assert.assertThat(receiveIOException, IsInstanceOf.instanceOf(SocketTimeoutException.class));
    Assert.assertThat(receiveIOException.getMessage(), Matchers.is("Receive timed out"));

    Assert.assertThat(datagramServerThread.getReceivedString(), Matchers.nullValue(String.class));
  }

  class DatagramServerThread extends Thread {

    private final DatagramSocket datagramServerSocket;
    private IOException receiveIOException;
    private String receivedString;

    public DatagramServerThread() throws SocketException {
      datagramServerSocket = new DatagramSocket();

      // XXX: set wait receive timeout
      datagramServerSocket.setSoTimeout(10000);
    }

    @Override
    public void run() {
      final byte[] bytes = new byte[4096];
      final DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);

      try {
        datagramServerSocket.receive(datagramPacket);
        receivedString = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
      } catch (IOException e) {
        receiveIOException = e;
      }

      datagramServerSocket.disconnect();
      datagramServerSocket.close();
    }

    public void setSoTimeout(int soTimeoutMillis) throws SocketException {
      this.datagramServerSocket.setSoTimeout(soTimeoutMillis);
    }

    public IOException getReceiveIOException() {
      return receiveIOException;
    }

    public String getReceivedString() {
      return receivedString;
    }

    public String getHostname() {
      return this.datagramServerSocket.getLocalAddress().getHostName();
    }

    public String getPort() {
      return String.valueOf(this.datagramServerSocket.getLocalPort());
    }
  }
}
