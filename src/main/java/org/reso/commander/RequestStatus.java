package org.reso.commander;

import org.reso.resoscript.Request;
import java.util.Date;

public class RequestStatus {
  private Request request;
  private Status status;
  private Date startDate, endDate;
  private String responseCode;
  private Exception failedRequestException;

  RequestStatus(Request request, Status status) {
    this.setRequest(request);
    this.setStatus(status);
  }

  public Request getRequest() {
    return request;
  }

  public void setRequest(Request request) {
    this.request = request;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public RequestStatus startTimer() {
    startDate = new Date();
    return this;
  }

  public RequestStatus stopTimer() {
    endDate = new Date();
    return this;
  }

  public long getElapsedTimeMillis() {
    return endDate.getTime() - startDate.getTime();
  }

  public void setFailedRequestException(Exception failedRequestException) {
    this.failedRequestException = failedRequestException;
  }

  public String getResponseCode() {
    return responseCode;
  }

  public void setResponseCode(String responseCode) {
    this.responseCode = responseCode;
  }

  public enum Status {
    STARTED, SUCCEEDED, FAILED, SKIPPED
  }
}