package gov.cms.madie.cqllibraryservice.repositories;

import java.util.Collection;
import java.util.List;

import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.LibraryActionLog;

public interface ActionLogRepository {

  /**
   * Performs a MongoDB Upsert operation based on the targetId. If a document with the given
   * targetId is found, the provided action will be pushed onto a list on the document. If no
   * document with the given targetId is found, a new one will be created with the provided action
   * as the sole item in the list.
   *
   * @param targetId field to search on
   * @param action action to push into the list of actions for the given targetId
   * @return true if upsert is successful, false otherwise
   */
  boolean pushEvent(String targetId, Action action, String collection);

  boolean pushEvent(String targetId, AccessControlAction action);

  List<LibraryActionLog> findAllActionLogs();

  Collection<LibraryActionLog> saveAllActionLogs(List<LibraryActionLog> actionLogs);

  void removeActionsByUsers(List<String> users, String collection);

  Collection<LibraryActionLog> updateAllActionLogs(List<LibraryActionLog> actionLogs);
}
