package org.reso.commander;

import org.reso.resoscript.Request;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The Stats class keeps information about a Commander run.
 */
public class Stats {
  Date startDate, endDate;
  private HashMap<Request, RequestStatus> requestStatuses = new HashMap<>();

  public Stats () { /* */ }

  public void startRequest(Request request) {
    if (request != null) {
      requestStatuses.put(request, new RequestStatus(request, RequestStatus.Status.STARTED).startTimer());
    }
  }

  public void updateRequestStatus(Request request, RequestStatus.Status status) {
    // only affects existing requestStatuses, use startRequest to register a RequestStatus
    requestStatuses.get(request).stopTimer().setStatus(status);
  }

  public void updateRequestStatus(Request request, RequestStatus.Status status, Exception ex) {
    requestStatuses.get(request).setFailedRequestException(ex);
    updateRequestStatus(request, status);
  }

  public int totalRequestCount() {
    return getRequestStatuses().size();
  }

  public int getRequestStatusCount(RequestStatus.Status status) {
    return filterByStatus(status, getRequestStatuses().values()).size();
  }

  public Collection<RequestStatus> filterByStatus(RequestStatus.Status status) {
    return filterByStatus(status, getRequestStatuses().values());
  }

  public Collection<RequestStatus> filterByStatus(RequestStatus.Status status, Collection<RequestStatus> collection) {
    return collection.stream()
        .filter(requestStatus -> requestStatus.getStatus().compareTo(status) == 0).collect(Collectors.toList());
  }

  public Collection<RequestStatus> filterByMetallicCertification(String metallicName) {
    return filterByMetallicCertification(metallicName, getRequestStatuses().values());
  }

  public Collection<RequestStatus> filterByMetallicCertification(String metallicName, Collection<RequestStatus> collection) {
    return collection.stream()
        .filter(requestStatus -> requestStatus.getRequest().getMetallicLevel().compareTo(metallicName) == 0).collect(Collectors.toList());
  }

  public Collection<RequestStatus> filterByCapability(String capabilityName) {
    return filterByCapability(capabilityName, getRequestStatuses().values());
  }

  public Collection<RequestStatus> filterByCapability(String capabilityName, Collection<RequestStatus> collection) {
    return collection.stream()
        .filter(requestStatus -> requestStatus.getRequest().getCapability().compareTo(capabilityName) == 0).collect(Collectors.toList());
  }

  public HashMap<String, Integer> metallicStats;
  public HashMap<String, Integer> capabilitiesStats;

  public HashMap<Request, RequestStatus> getRequestStatuses() {
    return requestStatuses;
  }

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
