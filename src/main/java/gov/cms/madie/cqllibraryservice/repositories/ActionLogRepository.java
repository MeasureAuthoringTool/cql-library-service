package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.Action;

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
  boolean pushEvent(String targetId, Action action);
}
