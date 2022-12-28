package com.huawei.operation;

import com.huawei.entity.vo.codecheck.project.ProjectInfoVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectInfoOperation {
    @Autowired
    @Qualifier("codeCheckMongoTemplate")
    private MongoTemplate mongoTemplate;

    /**
     * 查询所有项目信息
     *
     * @return
     */
    public List<ProjectInfoVo> getAll() {
        Criteria projectId = Criteria.where("projectId").exists(true).ne("").ne(null);
        Query query = Query.query(projectId);
        return mongoTemplate.find(query, ProjectInfoVo.class);
    }
}
