package org.reso.commander;

import org.reso.resoscript.Request;

import java.util.*;
import java.util.stream.Collectors;

public class Stats {
  Date startDate, endDate;
  private HashMap<Request, RequestStatus> requestStatuses = new HashMap<>();

  public Stats () { /* */ }

  public HashMap<Request, RequestStatus> getRequestStatuses() {
    return requestStatuses;
  }

  public void startRequest(Request request) {
    if (request != null) {
      requestStatuses.put(request, new RequestStatus(request, RequestStatus.Status.STARTED).startTimer());
    }
  }

  public void updateRequestStatus(Request request, RequestStatus.Status status) {
    // only affects existing requestStatuses, use startRequest to register a RequestStatus
//    if (request != null && status != null && requestStatuses.containsKey(request)) {
      requestStatuses.get(request).stopTimer().setStatus(status);
//    }
  }

  public void updateRequestStatus(Request request, RequestStatus.Status status, Exception ex) {
    requestStatuses.get(request).setFailedRequestException(ex);
    updateRequestStatus(request, status);
  }

  public int totalRequestCount() {
    return getRequestStatuses().size();
  }

  public int getRequestStatusCount(RequestStatus.Status status) {
    return filterRequestStatuses(status).size();
  }

  public Collection<RequestStatus> filterRequestStatuses(RequestStatus.Status status) {
    return getRequestStatuses().values().stream()
        .filter(requestStatus -> requestStatus.getStatus().compareTo(status) == 0).collect(Collectors.toList());
  }

  public HashMap<String, Integer> metallicStats;
  public HashMap<String, Integer> capabilitiesStats;


  public void startTimer() {
    startDate = new Date();
  }

  public void stopTimer() {
    endDate = new Date();
  }

  public long getElapsedTimeMillis() {
    return endDate.getTime() - startDate.getTime();
  }

}
