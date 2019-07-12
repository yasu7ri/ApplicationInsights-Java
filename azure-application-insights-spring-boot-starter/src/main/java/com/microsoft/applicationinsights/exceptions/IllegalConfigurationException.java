package com.microsoft.applicationinsights.exceptions;

public class IllegalConfigurationException extends IllegalStateException {

  public IllegalConfigurationException(String s) {
    super(s);
  }

  public IllegalConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
