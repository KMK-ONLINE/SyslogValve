package org.apache.catalina;

public class MessageLengthLimitException extends Exception {

  public MessageLengthLimitException(int actualMessageLength, int messageLengthLimit) {
    // XXX: e.g. Message length limit exceeded, message length: 25, limit: 10
    super(
        new StringBuilder().
            append("Message length limit exceeded, message length: ").
            append(actualMessageLength).
            append(", limit: ").
            append(messageLengthLimit).
            toString()
    );
  }
}
