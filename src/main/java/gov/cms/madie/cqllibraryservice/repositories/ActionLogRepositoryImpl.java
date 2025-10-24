package gov.cms.madie.cqllibraryservice.repositories;

import com.mongodb.client.result.UpdateResult;

import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.LibraryActionLog;
import gov.cms.madie.models.common.LibrarySetActionLog;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
public class ActionLogRepositoryImpl implements ActionLogRepository {

  private final MongoTemplate mongoTemplate;

  public ActionLogRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public boolean pushEvent(String targetId, Action action, String collection) {
    if (targetId == null || targetId.isEmpty() || action == null) {
      return false;
    }
    Update update = new Update();
    UpdateResult upsert =
        mongoTemplate.upsert(
            new Query(Criteria.where("targetId").is(targetId)),
            update.push("actions").value(action),
            collection);
    return upsert.getUpsertedId() != null || upsert.getModifiedCount() == 1;
  }

  @Override
  public boolean pushEvent(String targetId, AccessControlAction accessControlAction) {
    if (targetId == null || targetId.isEmpty() || accessControlAction == null) {
      return false;
    }
    Update update = new Update();
    UpdateResult upsert =
        mongoTemplate.upsert(
            new Query(Criteria.where("targetId").is(targetId)),
            update.push("actions").value(accessControlAction),
            LibrarySetActionLog.class);
    return upsert.getUpsertedId() != null || upsert.getModifiedCount() == 1;
  }

  @Override
  public List<LibraryActionLog> findAllActionLogs() {
    return mongoTemplate.findAll(LibraryActionLog.class, "actionLog");
  }

  @Override
  public Collection<LibraryActionLog> saveAllActionLogs(List<LibraryActionLog> actionLogs) {
    return mongoTemplate.insert(actionLogs, "actionLog");
  }

  @Override
  @Transactional
  public void removeActionsByUsers(List<String> users, String collection) {
    log.debug("Removing Actions performed by users: [{}] from collection: [{}]", users, collection);
    Query query = new Query(Criteria.where("actions.performedBy").in(users));
    Update update =
        new Update().pull("actions", Query.query(Criteria.where("performedBy").in(users)));

    UpdateResult result = mongoTemplate.updateMulti(query, update, collection);
    log.debug(
        "removeActionsByUsers: UpdateResult: matchedAcount = "
            + result.getMatchedCount()
            + " modifiedCount = "
            + result.getModifiedCount());

    Query emptyActionsQuery = new Query(Criteria.where("actions").size(0));
    mongoTemplate.remove(emptyActionsQuery, collection);
  }
}
