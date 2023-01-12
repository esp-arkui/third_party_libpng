package com.huawei.operation;

import com.huawei.entity.pojo.CodeCheckResultSummaryVo;
import com.huawei.enums.CodeCheckCollectionName;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResultSummaryOperation {
    @Autowired
    @Qualifier("codeCheckMongoTemplate")
    private MongoTemplate mongoTemplate;
    private static final Logger logger = LoggerFactory.getLogger(ResultSummaryOperation.class);

    private static final String[] sortedField = new String[]{
            "issueCount", "solve_count", "ignoreCount"
    };

    /**
     * 通过日期和任务id查询summary
     *
     * @param currentDate 当前时间
     * @param taskId      任务id
     * @return CodeCheckResultSummaryVo summary数据
     */
    public CodeCheckResultSummaryVo getSummaryByTaskIdAndDate(String currentDate, String taskId) {
        Query query = Query.query(Criteria.where("taskId").is(taskId).and("date").is(currentDate));
        return mongoTemplate.findOne(query, CodeCheckResultSummaryVo.class, CodeCheckCollectionName.TASK_RESULT_SUMMARY);
    }

    /**
     * 根据taskId和日期更新summary数据
     *
     * @param summaryByNameAndBranch summary数据
     * @param currentDate            当前日期
     */
    public void updateSummaryByTaskIdAndDate(CodeCheckResultSummaryVo summaryByNameAndBranch, String currentDate) {
        Query query = Query.query(Criteria.where("taskId").is(summaryByNameAndBranch.getTaskId()).and("date").is(currentDate));
        //不存在则新增，存在则替换
        CodeCheckResultSummaryVo replacedOne = mongoTemplate.findOne(query, CodeCheckResultSummaryVo.class, CodeCheckCollectionName.TASK_RESULT_SUMMARY);
        if (ObjectUtils.isEmpty(replacedOne)) {
            mongoTemplate.save(summaryByNameAndBranch, CodeCheckCollectionName.TASK_RESULT_SUMMARY);
        } else {
            mongoTemplate.findAndReplace(query, summaryByNameAndBranch);
        }
        logger.info(" ------------------->  summary info have saved  <---------------- ");
        //mongoTemplate.findAndReplace(query,summaryByNameAndBranch);
    }

    /**
     *
     * @param taskId 任务Id
     * @return 最新summary数据
     */
    public List<CodeCheckResultSummaryVo> getLastSummaryByTaskId(String taskId, String date) {
        List<AggregationOperation> operations = new ArrayList<>();
        Criteria criteria = Criteria.where("taskId").is(taskId);
        if (StringUtils.isNotBlank(date)) {
            criteria.and("date").is(date);
        }
        operations.add(Aggregation.match(criteria));
        operations.add(Aggregation.sort(Sort.by(Sort.Order.desc("date"))));
        operations.add(Aggregation.limit(1));
        return mongoTemplate.aggregate(Aggregation.newAggregation(operations),
                CodeCheckCollectionName.TASK_RESULT_SUMMARY, CodeCheckResultSummaryVo.class).getMappedResults();
    }

    /**
     * 通过id更新summary
     *
     * @param summaryByNameAndBranch summary数据
     */
    public void updateById(CodeCheckResultSummaryVo summaryByNameAndBranch) {
        Query query = Query.query(Criteria.where("id").is(summaryByNameAndBranch.getId()));
        mongoTemplate.findAndReplace(query, summaryByNameAndBranch);
    }
}

