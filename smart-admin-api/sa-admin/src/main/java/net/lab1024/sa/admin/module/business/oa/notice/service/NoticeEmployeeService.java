package net.lab1024.sa.admin.module.business.oa.notice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import net.lab1024.sa.admin.module.business.oa.notice.constant.NoticeVisibleRangeDataTypeEnum;
import net.lab1024.sa.admin.module.business.oa.notice.dao.NoticeDao;
import net.lab1024.sa.admin.module.business.oa.notice.domain.form.NoticeEmployeeQueryForm;
import net.lab1024.sa.admin.module.business.oa.notice.domain.form.NoticeViewRecordQueryForm;
import net.lab1024.sa.admin.module.business.oa.notice.domain.vo.*;
import net.lab1024.sa.admin.module.system.department.service.DepartmentService;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import net.lab1024.sa.admin.module.system.employee.service.EmployeeService;
import com.mart.sszx.common.domain.PageResult;
import com.mart.sszx.common.domain.ResponseDTO;
import com.mart.sszx.common.util.SmartBeanUtil;
import com.mart.sszx.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * 员工查看 通知。公告
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-08-12 21:40:39
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class NoticeEmployeeService {

    @Resource
    private NoticeDao noticeDao;

    @Resource
    private NoticeService noticeService;

    @Resource
    private DepartmentService departmentService;

    @Resource
    private EmployeeService employeeService;

    /**
     * 查询我的 通知、公告清单
     */
    public ResponseDTO<PageResult<NoticeEmployeeVO>> queryList(Long requestEmployeeId, NoticeEmployeeQueryForm noticeEmployeeQueryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(noticeEmployeeQueryForm);

        //获取请求人的 部门及其子部门
        List<Long> employeeDepartmentIdList = Lists.newArrayList();
        EmployeeEntity employeeEntity = employeeService.getById(requestEmployeeId);
        if (employeeEntity.getDepartmentId() != null) {
            employeeDepartmentIdList = departmentService.selfAndChildrenIdList(employeeEntity.getDepartmentId());
        }

        List<NoticeEmployeeVO> noticeList = null;
        //只查询未读的
        if (noticeEmployeeQueryForm.getNotViewFlag() != null && noticeEmployeeQueryForm.getNotViewFlag()) {
            noticeList = noticeDao.queryEmployeeNotViewNotice(page,
                    requestEmployeeId,
                    noticeEmployeeQueryForm,
                    employeeDepartmentIdList,
                    false,
                    employeeEntity.getAdministratorFlag(),
                    NoticeVisibleRangeDataTypeEnum.DEPARTMENT.getValue(),
                    NoticeVisibleRangeDataTypeEnum.EMPLOYEE.getValue());
        } else {
            // 查询全部
            noticeList = noticeDao.queryEmployeeNotice(page,
                    requestEmployeeId,
                    noticeEmployeeQueryForm,
                    employeeDepartmentIdList,
                    false,
                    employeeEntity.getAdministratorFlag(),
                    NoticeVisibleRangeDataTypeEnum.DEPARTMENT.getValue(),
                    NoticeVisibleRangeDataTypeEnum.EMPLOYEE.getValue());
        }
        // 设置发布日期
        noticeList.forEach(notice -> notice.setPublishDate(notice.getPublishTime().toLocalDate()));

        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, noticeList));
    }


    /**
     * 查询我的 待查看的 通知、公告清单
     */
    public ResponseDTO<NoticeDetailVO> view(Long requestEmployeeId, Long noticeId, String ip, String userAgent) {
        NoticeUpdateFormVO updateFormVO = noticeService.getUpdateFormVO(noticeId);
        if (updateFormVO == null || Boolean.TRUE.equals(updateFormVO.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("通知公告不存在");
        }

        EmployeeEntity employeeEntity = employeeService.getById(requestEmployeeId);
        if (!updateFormVO.getAllVisibleFlag() && !checkVisibleRange(updateFormVO.getVisibleRangeList(), requestEmployeeId, employeeEntity.getDepartmentId())) {
            return ResponseDTO.userErrorParam("对不起，您没有权限查看内容");
        }

        NoticeDetailVO noticeDetailVO = SmartBeanUtil.copy(updateFormVO, NoticeDetailVO.class);
        long viewCount = noticeDao.viewRecordCount(noticeId, requestEmployeeId);
        if (viewCount == 0) {
            noticeDao.insertViewRecord(noticeId, requestEmployeeId, ip, userAgent, 1);
        } else {
            noticeDao.updateViewRecord(noticeId, requestEmployeeId, ip, userAgent);
        }

        return ResponseDTO.ok(noticeDetailVO);
    }

    /**
     * 校验是否有查看权限的范围
     *
     */
    public boolean checkVisibleRange(List<NoticeVisibleRangeVO> visibleRangeList, Long employeeId, Long departmentId) {
        // 员工范围
        boolean anyMatch = visibleRangeList.stream().anyMatch(e -> NoticeVisibleRangeDataTypeEnum.EMPLOYEE.equalsValue(e.getDataType()) && Objects.equals(e.getDataId(), employeeId));
        if (anyMatch) {
            return true;
        }

        //部门范围
        List<Long> visibleDepartmentIdList = visibleRangeList.stream().filter(e -> NoticeVisibleRangeDataTypeEnum.DEPARTMENT.equalsValue(e.getDataType()))
                .map(NoticeVisibleRangeVO::getDataId).collect(Collectors.toList());

        for (Long visibleDepartmentId : visibleDepartmentIdList) {
            List<Long> departmentIdList = departmentService.selfAndChildrenIdList(visibleDepartmentId);
            if (departmentIdList.contains(departmentId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 分页查询  查看记录
     */
    public PageResult<NoticeViewRecordVO> queryViewRecord(NoticeViewRecordQueryForm noticeViewRecordQueryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(noticeViewRecordQueryForm);
        List<NoticeViewRecordVO> noticeViewRecordList = noticeDao.queryNoticeViewRecordList(page, noticeViewRecordQueryForm);
        return SmartPageUtil.convert2PageResult(page, noticeViewRecordList);
    }
}
