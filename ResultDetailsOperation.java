package com.huawei.operation;

import com.huawei.ci.common.codecheck.RestCodeCheckUtils;
import com.huawei.ci.common.contant.ProjectContant;
import com.huawei.entity.pojo.CodeCheckTaskIncVo;
import com.huawei.entity.pojo.QueryDefectDetailModel;
import com.huawei.entity.vo.ciinfo.codecheck.DefectVo;
import com.huawei.entity.vo.ciinfo.codecheck.Revision;
import com.huawei.entity.vo.codecheck.codecheckdetails.ReportVo;
import com.huawei.entity.vo.codecheck.eventModule.QueryDetailModel;
import com.huawei.entity.vo.codecheck.eventModule.ReportResultModel;
import com.huawei.entity.vo.codecheck.eventModule.RuleReportModel;
import com.huawei.entity.vo.codecheck.eventModule.ToolReportModel;
import com.huawei.enums.CodeCheckCollectionName;
import com.huawei.enums.CodeCheckConstants;
import com.mongodb.client.result.UpdateResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

@Component
public class ResultDetailsOperation {
    private static final Logger logger = LoggerFactory.getLogger(ResultDetailsOperation.class);

    private static final String FOSSSCAN = "fossscan";

    private static final String PROGRAM_SPECIFICATION = "coding standards";

    private static final String SENSITIVE_WORD = "wordstool";

    private static final String OAT = "oat";

    //每一批修改的问题数
    private static final int defectNum = 120;

    @Autowired
    private RestCodeCheckUtils restCodeCheckUtils;

    @Autowired
    @Qualifier("codeCheckMongoTemplate")
    private MongoTemplate mongoTemplate;

    /**
     * report 统计detail的方法
     *
     * @param type             type
     * @param taskId           taskId
     * @param queryDetailModel queryDetailModel
     * @param uuid             uuid
     * @return HashMap
     */
    public HashMap getTaskIssuesReport(String type, String taskId, QueryDetailModel queryDetailModel, String uuid) {
        boolean full = type.equalsIgnoreCase(CodeCheckConstants.HARMONY_FULL);
        Criteria criteria = getTaskIssuesCommonCriteria(full, taskId, queryDetailModel, uuid);
        if (StringUtils.isNotBlank(queryDetailModel.getDefectStatus())) {
            criteria.and("defectStatus").is(queryDetailModel.getDefectStatus());
        }
        if (StringUtils.isNotBlank(queryDetailModel.getFilePath())) {
            criteria.and("filepath").regex(queryDetailModel.getFilePath());
        }

        if (StringUtils.isNotBlank(queryDetailModel.getFileName())) {
            criteria.and(full ? "fileName" : "filepath").regex(queryDetailModel.getFileName());
        }
        if (StringUtils.isNotBlank(queryDetailModel.getRule())) {
            criteria.and("ruleName").regex(queryDetailModel.getRule());
        }
        List<AggregationOperation> operations = new ArrayList<>();
        operations.add(match(criteria));
        operations.add(group("defectLevel", "defectStatus", "fileName", "ruleSystemTags", "ruleName", "defectContent")
                .first("defectLevel").as("defectLevel")
                .first("defectStatus").as("defectStatus")
                .first("fileName").as("fileName")
                .first("ruleSystemTags").as("ruleSystemTags")
                .first("ruleName").as("ruleName")
                .first("defectContent").as("defectContent").count().as("total"));
        List<ReportResultModel> mappedResults = new ArrayList<>();
        if (full) {
            mappedResults = mongoTemplate.aggregate(newAggregation(operations),
                    CodeCheckCollectionName.TASK_RESULT_DETAILS, ReportResultModel.class).getMappedResults();
        } else {
            for (int i = 0; i < 10; ++i) {
                mappedResults.addAll(mongoTemplate.aggregate(newAggregation(operations),
                        CodeCheckCollectionName.T_TASK_INC_RESULT_DETAILS + i,
                        ReportResultModel.class).getMappedResults());
            }
        }
        Map<String, Integer> levelReport = new HashMap();
        Map<String, Integer> statusReport = new HashMap();
        Map<String, Integer> fileNameReport = new LinkedHashMap<>();
        Map<String, Integer> contentReport = new LinkedHashMap<>();
        Map<String, Integer> fossReport = new LinkedHashMap<>();
        List<ToolReportModel> toolReportModels = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(mappedResults)) {
            //根据等级分类统计
            levelReport = mappedResults.stream()
                    .collect(Collectors.groupingBy(ReportResultModel::getDefectLevel, Collectors.reducing(0, ReportResultModel::getTotal, Integer::sum)));
            //根据状态分类统计
            statusReport = mappedResults.stream()
                    .collect(Collectors.groupingBy(ReportResultModel::getDefectStatus, Collectors.reducing(0, ReportResultModel::getTotal, Integer::sum)));
            //根据工具统计
            mappedResults.stream()
                    .collect(Collectors.groupingBy(ReportResultModel::getRuleSystemTags, Collectors.reducing(0, ReportResultModel::getTotal, Integer::sum)))
                    .entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> {
                        ToolReportModel toolReportModel = new ToolReportModel();
                        toolReportModel.setToolName(e.getKey());
                        toolReportModel.setTotal(e.getValue());
                        toolReportModels.add(toolReportModel);
                    });
            //根据规则名称分类统计排序
            if (CollectionUtils.isNotEmpty(toolReportModels)) {
                for (ToolReportModel toolReportModel : toolReportModels) {
                    List<RuleReportModel> ruleReportModels = new ArrayList<>();
                    mappedResults.stream().filter(mappedResult -> mappedResult.getRuleSystemTags().equals(toolReportModel.getToolName())).collect(Collectors.groupingBy(ReportResultModel::getRuleName, Collectors.reducing(0, ReportResultModel::getTotal, Integer::sum)))
                            .entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .forEach(e -> {
                                RuleReportModel ruleReportModel = new RuleReportModel();
                                ruleReportModel.setRuleName(e.getKey());
                                ruleReportModel.setTotal(e.getValue());
                                ruleReportModels.add(ruleReportModel);
                            });
                    toolReportModel.setToolName(StringUtils.isEmpty(toolReportModel.getToolName()) ? PROGRAM_SPECIFICATION : toolReportModel.getToolName());
                    toolReportModel.setRuleReports(ruleReportModels);
                }
            }
            List<String> toolNameList = toolReportModels.stream().map(ToolReportModel::getToolName).distinct().collect(Collectors.toList());
            if (!toolNameList.contains(PROGRAM_SPECIFICATION)) {
                ToolReportModel toolReportModel = new ToolReportModel();
                toolReportModel.setToolName(PROGRAM_SPECIFICATION);
                toolReportModel.setTotal(0);
                toolReportModels.add(toolReportModel);
            }
            if (!toolNameList.contains(SENSITIVE_WORD)) {
                ToolReportModel toolReportModel = new ToolReportModel();
                toolReportModel.setToolName(SENSITIVE_WORD);
                toolReportModel.setTotal(0);
                toolReportModels.add(toolReportModel);
            }
            if (!toolNameList.contains(OAT)) {
                ToolReportModel toolReportModel = new ToolReportModel();
                toolReportModel.setToolName(OAT);
                toolReportModel.setTotal(0);
                toolReportModels.add(toolReportModel);
            }
            //根据描述分类统计排序
            mappedResults.stream()
                    .collect(Collectors.groupingBy(ReportResultModel::getDefectContent, Collectors.reducing(0, ReportResultModel::getTotal, Integer::sum)))
                    .entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(e -> contentReport.put(e.getKey(), e.getValue()));
            //根据文件分类统计统计排序
            mappedResults.stream()
                    .collect(Collectors.groupingBy(ReportResultModel::getFileName, Collectors.reducing(0, ReportResultModel::getTotal, Integer::sum)))
                    .entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(e -> fileNameReport.put(e.getKey(), e.getValue()));
        }

        //fossscan问题加入
        Criteria criteriaFalse = Criteria.where("taskId").is(taskId).and("isConfirm").is(false);
        Criteria criteriaTrue = Criteria.where("taskId").is(taskId).and("isConfirm").is(true);
        if (full) {
            criteriaFalse.and("date").is(queryDetailModel.getDate());
            criteriaTrue.and("date").is(queryDetailModel.getDate());
        }
        {
            criteriaFalse.and("uuid").is(uuid);
            criteriaTrue.and("uuid").is(uuid);
        }
        Query fossQuery = new Query(criteriaFalse);
        long fosscount = mongoTemplate.count(fossQuery, CodeCheckCollectionName.FOSSSCAN_FRAGMENT);
        fossQuery = new Query(criteriaTrue);
        long fosscountConfirm = mongoTemplate.count(fossQuery, CodeCheckCollectionName.FOSSSCAN_FRAGMENT);
        fossReport.put("0", (int) fosscount);
        fossReport.put("2", (int) fosscountConfirm);

        HashMap issueReport = new HashMap();
        issueReport.put("levelReport", levelReport);
        issueReport.put("statusReport", statusReport);
        issueReport.put("fileNameReport", fileNameReport);
        issueReport.put("contentReport", contentReport);
        issueReport.put("ruleSystemTagsReport", toolReportModels);
        issueReport.put("fossReport", fossReport);
        return issueReport;
    }

    /**
     * 获取概览页问题总数分类查询和详情页问题分类查询公共条件获取
     *
     * @param isFull           全量或者增量标志
     * @param taskId           任务id
     * @param queryDetailModel 查询条件参数
     * @param uuid             增量任务的uuid
     * @return Criteria 条件
     */
    private Criteria getTaskIssuesCommonCriteria(boolean isFull, String taskId, QueryDetailModel queryDetailModel,
                                                 String uuid) {
        Criteria criteria = Criteria.where("taskId").is(taskId).and("ruleSystemTags").ne(FOSSSCAN);
        String date = "";
        if (!isFull) {
            criteria.and("uuid").is(uuid);
        } else if (StringUtils.isBlank(queryDetailModel.getDate())) {
            //如果时间为空就去查询detail的最新时间
            Query query = new Query();
            query.fields().include("date");
            query.with(Sort.by(Sort.Order.desc("date")));
            query.addCriteria(Criteria.where("taskId").is(taskId));
            DefectVo defectVo = mongoTemplate.findOne(query, DefectVo.class,
                    CodeCheckCollectionName.TASK_RESULT_DETAILS);
            if (defectVo != null) {
                criteria.and("date").is(defectVo.getDate());
                date = defectVo.getDate();
            }
        } else {
            criteria.and("date").is(queryDetailModel.getDate());
        }
        return criteria;
    }

    /**
     * 修改事件问题状态
     */
    public int updateEventDefect(String userId, String userName, QueryDetailModel queryDetailModel, String eventId) {
        //统计修改总数
        int num = 0;
        if (queryDetailModel.getDefectStatus() != null && queryDetailModel.getIds() != null) {
            Query query = new Query(Criteria.where("uuid").is(eventId).and("_id").in(queryDetailModel.getIds()));
            //获取原问题列表
            List<DefectVo> defectVoList = new ArrayList<>();
            for (int i = 0; i < 10; ++i) {
                defectVoList.addAll(mongoTemplate.find(query, DefectVo.class,
                        CodeCheckCollectionName.T_TASK_INC_RESULT_DETAILS + i));
            }
            defectVoList =
                    defectVoList.stream().filter(defectVo -> !StringUtils.equals(defectVo.getDefectCheckerName().substring(0, 3), "OAT")).collect(Collectors.toList());
            if (defectVoList.size() > 0) {
                Query taskQuery = new Query(Criteria.where("taskId").is(defectVoList.get(0).getTaskId()).and("uuid").is(eventId));
                CodeCheckTaskIncVo codeCheckTaskIncVo = mongoTemplate.findOne(taskQuery, CodeCheckTaskIncVo.class, CodeCheckCollectionName.TASK_INC);
                if (codeCheckTaskIncVo != null && codeCheckTaskIncVo.getProcessing() != 2) {
                    return -1;
                }
                for (int start = 0; start < defectVoList.size(); start += defectNum) {
                    int end = defectVoList.size() - start <= defectNum ? defectVoList.size() - 1 : start + defectNum - 1;
                    StringBuilder ids = new StringBuilder(defectVoList.get(start).getDefectId() == null ? "" : defectVoList.get(start).getDefectId());
                    for (int i = start + 1; i <= end; i++) {
                        ids.append(",").append(defectVoList.get(i).getDefectId() == null ? "" : defectVoList.get(i).getDefectId());
                    }
                    if (defectVoList.size() > end && restCodeCheckUtils.updateDefectStatus(defectVoList.get(start).getTaskId(), ids.toString(),
                            Integer.parseInt(queryDetailModel.getDefectStatus()), ProjectContant.RequestConstant.REGION)) {
                        //修改库
                        updateDefectsImpl(defectVoList.subList(start, end + 1), userId, userName, queryDetailModel.getDefectStatus(), 1);
                        num += end - start + 1;
                    } else {
                        return 0;
                    }
                }
            } else {
                logger.warn("no defectIds");
                return 0;
            }
        } else {
            logger.error("param is null");
            return 0;
        }
        return num;
    }

    public void updateDefectsImpl(List<DefectVo> defectVoList, String userId, String userName, String status, int inc) {
        String detailsName;
        String summaryName;
        if (inc == 1) {
            detailsName = CodeCheckCollectionName.TASK_INC_RESULT_DETAILS;
            summaryName = CodeCheckCollectionName.TASK_INC_RESULT_SUMMARY;
        } else {
            detailsName = CodeCheckCollectionName.TASK_RESULT_DETAILS;
            summaryName = CodeCheckCollectionName.TASK_RESULT_SUMMARY;
        }
        //遍历问题列表并统计未解决和已忽略问题数
        int def0 = 0, def2 = 0;
        for (DefectVo defectVo : defectVoList) {
            if (defectVo.getDefectStatus().equals("0")) {
                def0++;
            }
            if (defectVo.getDefectStatus().equals("2")) {
                def2++;
            }
            Revision revision = new Revision();
            revision.setNewStatus(Integer.parseInt(status));
            revision.setPreviousStatus(Integer.getInteger(defectVo.getDefectStatus()));
            revision.setUserId(userId);
            revision.setUserName(userName);
            revision.setTimestamp(LocalDateTime.now());
            Query query = new Query(Criteria.where("defectId").is(defectVo.getDefectId()));
            Update update = new Update();
            update.set("revision", revision);
            update.set("defectStatus", status);
            if (StringUtils.equals(detailsName, CodeCheckCollectionName.TASK_INC_RESULT_DETAILS)) {
                for (int i = 0; i < 10; ++i) {
                    mongoTemplate.upsert(query, update, CodeCheckCollectionName.T_TASK_INC_RESULT_DETAILS + i);
                }
            }
            mongoTemplate.upsert(query, update, detailsName);
        }
        //修改summry
        Query query1;
        if (inc == 1) {
            query1 = new Query(Criteria.where("taskId").is(defectVoList.get(0).getTaskId()).and("uuid").is(defectVoList.get(0).getUuid()));
        } else {
            query1 = new Query(Criteria.where("taskId").is(defectVoList.get(0).getTaskId()).and("date").is(defectVoList.get(0).getDate()));
        }
        ReportVo reportVo = mongoTemplate.findOne(query1, ReportVo.class, summaryName);
        if (reportVo != null) {
            if (status.equals("0")) {
                reportVo.setIssueCount(reportVo.getIssueCount() + defectVoList.size() - def0);
                reportVo.setIgnoreCount(reportVo.getIgnoreCount() - def2);
            } else if (status.equals("2")) {
                reportVo.setIssueCount(reportVo.getIssueCount() - def0);
                reportVo.setIgnoreCount(reportVo.getIgnoreCount() + defectVoList.size() - def2);
            }
            Update update1 = new Update();
            update1.set("issueCount", reportVo.getIssueCount());
            update1.set("new_count", reportVo.getIssueCount());
            update1.set("ignoreCount", reportVo.getIgnoreCount());
            mongoTemplate.updateFirst(query1, update1, summaryName);
        }
    }

    /**
     * 每日代码检查问题查询条件
     *
     * @Params: queryDefectDetailModel 查询参数
     * @return: List<DefectVo> 问题详情列表
     */
    public List<DefectVo> queryDefects(QueryDefectDetailModel queryDefectDetailModel) {
        //根据是否传入uuid判断是全量的还是增量的问题详情
        Criteria criteria = Criteria.where("taskId").is(queryDefectDetailModel.getTaskId());
        String collectionName = CodeCheckCollectionName.TASK_RESULT_DETAILS;
        if (StringUtils.isNotBlank(queryDefectDetailModel.getDate())) {
            criteria.and("date").is(queryDefectDetailModel.getDate());
        }
        if (StringUtils.isNotBlank(queryDefectDetailModel.getDefectLevel())) {
            criteria.and("defectLevel").is(queryDefectDetailModel.getDefectLevel());
        }
        if (StringUtils.isNotBlank(queryDefectDetailModel.getDefectStatus())) {
            criteria.and("defectStatus").is(queryDefectDetailModel.getDefectStatus());
        }
        if (StringUtils.isNotBlank(queryDefectDetailModel.getRuleSystemTags())) {
            criteria.and("ruleSystemTags").is(queryDefectDetailModel.getRuleSystemTags());
        }
        if (StringUtils.isNotBlank(queryDefectDetailModel.getRuleName())) {
            criteria.and("ruleName").is(queryDefectDetailModel.getRuleName());
        }
        List<DefectVo> defectVos = new ArrayList<>();
        if (StringUtils.isNotBlank(queryDefectDetailModel.getUuid())) {
            criteria.and("uuid").is(queryDefectDetailModel.getUuid());
            for (int i = 0; i < 10; ++i) {
                defectVos.addAll(mongoTemplate.find(Query.query(criteria), DefectVo.class,
                        CodeCheckCollectionName.T_TASK_INC_RESULT_DETAILS + i));
            }
        } else {
            defectVos = mongoTemplate.find(Query.query(criteria), DefectVo.class, collectionName);
        }
        return defectVos;
    }

    /**
     * 根据问题id获取问题数据
     *
     * @param defectId             问题id
     * @param detailCollectionName 集合名称
     * @return DefectVo问题数据
     */
    public DefectVo getDefectById(String defectId, String detailCollectionName) {
        Query query = Query.query(Criteria.where("_id").is(defectId));
        return mongoTemplate.findOne(query, DefectVo.class, detailCollectionName);
    }

    /**
     * 根据id查询问题列表,获取有效的问题
     *
     * @param defectIds      问题id列表
     * @param collectionName 仓库名称
     * @return List<DefectVo> 问题列表
     */
    public List<DefectVo> getEffectiveDefectId(List<String> defectIds, String defectStatus, String collectionName) {
        Query query = Query.query(Criteria.where("_id").in(defectIds).and("defectStatus").is(defectStatus));
        return mongoTemplate.find(query, DefectVo.class, collectionName);
    }

    /**
     * 修改问题的版本号
     *
     * @param defectIds      问题id列表
     * @param revision       版本信息
     * @param collectionName 集合名称
     * @return
     */
    public long updateDetailRevision(List<String> defectIds, Revision revision, String collectionName) {
        Query query = Query.query(Criteria.where("defectId").in(defectIds));
        Update update = Update.update("revision", revision);
        update.set("defectStatus", String.valueOf(revision.getNewStatus()));
        UpdateResult updateResult = mongoTemplate.updateMulti(query, update, collectionName);
        return updateResult.getModifiedCount();
    }

    /**
     * 获取有效的issueKey
     *
     * @param defectIds 问题id
     * @param defectStatus 问题状态
     * @return
     */
    public List<String> getEffectiveIssueKey(List<String> defectIds, String defectStatus){
        Query query = Query.query(Criteria.where("_id").in(defectIds).and("defectStatus").is(defectStatus));
        return mongoTemplate.findDistinct(query, "issueKey",CodeCheckCollectionName.TASK_RESULT_DETAILS,String.class);
    }

    /**
     * 根据issueKey查询defectId
     *
     * @param taskId 任务id
     * @param issueKeys issueKey列表
     * @param defectStatus 问题状态
     * @return List<String> defectId列表
     */
    public List<String> getDefectIdByIssueKey(String taskId, List<String> issueKeys,String defectStatus) {
        Query query = Query.query(Criteria.where("taskId").is(taskId).and("defectStatus").is(defectStatus).and("issueKey").in(issueKeys));
        return mongoTemplate.findDistinct(query, "defectId",CodeCheckCollectionName.TASK_RESULT_DETAILS,String.class);
    }
}