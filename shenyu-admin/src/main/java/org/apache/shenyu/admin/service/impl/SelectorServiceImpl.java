/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.admin.service.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shenyu.admin.aspect.annotation.DataPermission;
import org.apache.shenyu.admin.aspect.annotation.Pageable;
import org.apache.shenyu.admin.listener.DataChangedEvent;
import org.apache.shenyu.admin.mapper.DataPermissionMapper;
import org.apache.shenyu.admin.mapper.PluginMapper;
import org.apache.shenyu.admin.mapper.RuleConditionMapper;
import org.apache.shenyu.admin.mapper.RuleMapper;
import org.apache.shenyu.admin.mapper.SelectorConditionMapper;
import org.apache.shenyu.admin.mapper.SelectorMapper;
import org.apache.shenyu.admin.model.dto.DataPermissionDTO;
import org.apache.shenyu.admin.model.dto.SelectorConditionDTO;
import org.apache.shenyu.admin.model.dto.SelectorDTO;
import org.apache.shenyu.admin.model.entity.DataPermissionDO;
import org.apache.shenyu.admin.model.entity.PluginDO;
import org.apache.shenyu.admin.model.entity.RuleDO;
import org.apache.shenyu.admin.model.entity.SelectorConditionDO;
import org.apache.shenyu.admin.model.entity.SelectorDO;
import org.apache.shenyu.admin.model.page.CommonPager;
import org.apache.shenyu.admin.model.page.PageResultUtils;
import org.apache.shenyu.admin.model.query.RuleConditionQuery;
import org.apache.shenyu.admin.model.query.RuleQuery;
import org.apache.shenyu.admin.model.query.SelectorConditionQuery;
import org.apache.shenyu.admin.model.query.SelectorQuery;
import org.apache.shenyu.admin.model.vo.SelectorConditionVO;
import org.apache.shenyu.admin.model.vo.SelectorVO;
import org.apache.shenyu.admin.service.SelectorService;
import org.apache.shenyu.admin.transfer.ConditionTransfer;
import org.apache.shenyu.admin.utils.CommonUpstreamUtils;
import org.apache.shenyu.admin.utils.JwtUtils;
import org.apache.shenyu.common.constant.AdminConstants;
import org.apache.shenyu.common.dto.ConditionData;
import org.apache.shenyu.common.dto.SelectorData;
import org.apache.shenyu.common.dto.convert.selector.DivideUpstream;
import org.apache.shenyu.common.dto.convert.selector.SpringCloudSelectorHandle;
import org.apache.shenyu.common.enums.ConfigGroupEnum;
import org.apache.shenyu.common.enums.DataEventTypeEnum;
import org.apache.shenyu.common.enums.MatchModeEnum;
import org.apache.shenyu.common.enums.OperatorEnum;
import org.apache.shenyu.common.enums.ParamTypeEnum;
import org.apache.shenyu.common.enums.PluginEnum;
import org.apache.shenyu.common.enums.SelectorTypeEnum;
import org.apache.shenyu.common.utils.ContextPathUtils;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link org.apache.shenyu.admin.service.SelectorService}.
 */
@Service
public class SelectorServiceImpl implements SelectorService {

    private final SelectorMapper selectorMapper;

    private final SelectorConditionMapper selectorConditionMapper;

    private final PluginMapper pluginMapper;

    private final RuleMapper ruleMapper;

    private final RuleConditionMapper ruleConditionMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final DataPermissionMapper dataPermissionMapper;

    private final UpstreamCheckService upstreamCheckService;

    public SelectorServiceImpl(final SelectorMapper selectorMapper,
                               final SelectorConditionMapper selectorConditionMapper,
                               final PluginMapper pluginMapper,
                               final RuleMapper ruleMapper,
                               final RuleConditionMapper ruleConditionMapper,
                               final ApplicationEventPublisher eventPublisher,
                               final DataPermissionMapper dataPermissionMapper,
                               final UpstreamCheckService upstreamCheckService) {
        this.selectorMapper = selectorMapper;
        this.selectorConditionMapper = selectorConditionMapper;
        this.pluginMapper = pluginMapper;
        this.ruleMapper = ruleMapper;
        this.ruleConditionMapper = ruleConditionMapper;
        this.eventPublisher = eventPublisher;
        this.dataPermissionMapper = dataPermissionMapper;
        this.upstreamCheckService = upstreamCheckService;
    }

    @Override
    public String registerDefault(final SelectorDTO selectorDTO) {
        SelectorDO selectorDO = SelectorDO.buildSelectorDO(selectorDTO);
        List<SelectorConditionDTO> selectorConditionDTOs = selectorDTO.getSelectorConditions();
        if (StringUtils.isEmpty(selectorDTO.getId())) {
            selectorMapper.insertSelective(selectorDO);
            selectorConditionDTOs.forEach(selectorConditionDTO -> {
                selectorConditionDTO.setSelectorId(selectorDO.getId());
                selectorConditionMapper.insertSelective(SelectorConditionDO.buildSelectorConditionDO(selectorConditionDTO));
            });
        }
        publishEvent(selectorDO, selectorConditionDTOs);
        return selectorDO.getId();
    }
    
    @Override
    public String registerDefault(final MetaDataRegisterDTO dto, final String pluginName, final String selectorHandler) {
        String contextPath = ContextPathUtils.buildContextPath(dto.getContextPath(), dto.getAppName());
        SelectorDO selectorDO = findByNameAndPluginName(contextPath, pluginName);
        if (Objects.isNull(selectorDO)) {
            return registerSelector(contextPath, pluginName, selectorHandler);
        }
        return selectorDO.getId();
    }

    /**
     * create or update selector.
     *
     * @param selectorDTO {@linkplain SelectorDTO}
     * @return rows
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createOrUpdate(final SelectorDTO selectorDTO) {
        int selectorCount;
        SelectorDO selectorDO = SelectorDO.buildSelectorDO(selectorDTO);
        List<SelectorConditionDTO> selectorConditionDTOs = selectorDTO.getSelectorConditions();
        if (StringUtils.isEmpty(selectorDTO.getId())) {
            selectorCount = selectorMapper.insertSelective(selectorDO);
            selectorConditionDTOs.forEach(selectorConditionDTO -> {
                selectorConditionDTO.setSelectorId(selectorDO.getId());
                selectorConditionMapper.insertSelective(SelectorConditionDO.buildSelectorConditionDO(selectorConditionDTO));
            });
            // check selector add
            if (dataPermissionMapper.listByUserId(JwtUtils.getUserInfo().getUserId()).size() > 0) {
                DataPermissionDTO dataPermissionDTO = new DataPermissionDTO();
                dataPermissionDTO.setUserId(JwtUtils.getUserInfo().getUserId());
                dataPermissionDTO.setDataId(selectorDO.getId());
                dataPermissionDTO.setDataType(AdminConstants.SELECTOR_DATA_TYPE);
                dataPermissionMapper.insertSelective(DataPermissionDO.buildPermissionDO(dataPermissionDTO));
            }

        } else {
            selectorCount = selectorMapper.updateSelective(selectorDO);
            //delete rule condition then add
            selectorConditionMapper.deleteByQuery(new SelectorConditionQuery(selectorDO.getId()));
            selectorConditionDTOs.forEach(selectorConditionDTO -> {
                selectorConditionDTO.setSelectorId(selectorDO.getId());
                SelectorConditionDO selectorConditionDO = SelectorConditionDO.buildSelectorConditionDO(selectorConditionDTO);
                selectorConditionMapper.insertSelective(selectorConditionDO);
            });
        }
        publishEvent(selectorDO, selectorConditionDTOs);
        updateDivideUpstream(selectorDO);
        return selectorCount;
    }

    @Override
    public int updateSelective(final SelectorDO selectorDO) {
        return selectorMapper.updateSelective(selectorDO);
    }

    /**
     * delete selectors.
     *
     * @param ids primary key.
     * @return rows
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(final List<String> ids) {
        for (String id : ids) {

            final SelectorDO selectorDO = selectorMapper.selectById(id);
            final PluginDO pluginDO = pluginMapper.selectById(selectorDO.getPluginId());

            selectorMapper.delete(id);
            selectorConditionMapper.deleteByQuery(new SelectorConditionQuery(id));
            dataPermissionMapper.deleteByDataId(id);

            //if divide selector delete
            if (PluginEnum.DIVIDE.getName().equals(pluginDO.getName())) {
                UpstreamCheckService.removeByKey(selectorDO.getName());
            }

            // publish delete event of Selector
            eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.SELECTOR, DataEventTypeEnum.DELETE,
                    Collections.singletonList(SelectorDO.transFrom(selectorDO, pluginDO.getName(), null))));

            // delete rule and ruleCondition
            final List<RuleDO> ruleDOList = ruleMapper.selectByQuery(new RuleQuery(id, null, null));
            if (CollectionUtils.isNotEmpty(ruleDOList)) {
                for (RuleDO ruleDO : ruleDOList) {
                    ruleMapper.delete(ruleDO.getId());
                    ruleConditionMapper.deleteByQuery(new RuleConditionQuery(ruleDO.getId()));
                    // send delete selectors event
                    eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.RULE, DataEventTypeEnum.DELETE,
                            Collections.singletonList(RuleDO.transFrom(ruleDO, pluginDO.getName(), null))));

                }
            }
        }
        return ids.size();
    }

    /**
     * find selector by id.
     *
     * @param id primary key.
     * @return {@linkplain SelectorVO}
     */
    @Override
    public SelectorVO findById(final String id) {
        return SelectorVO.buildSelectorVO(selectorMapper.selectById(id),
                selectorConditionMapper.selectByQuery(
                        new SelectorConditionQuery(id))
                        .stream()
                        .map(SelectorConditionVO::buildSelectorConditionVO)
                        .collect(Collectors.toList()));
    }

    @Override
    public SelectorDO findByName(final String name) {
        return selectorMapper.selectByName(name);
    }
    
    /**
     * Find by name and plugin id selector do.
     *
     * @param name the name
     * @param pluginName the plugin name
     * @return the selector do
     */
    @Override
    public SelectorDO findByNameAndPluginName(final String name, final String pluginName) {
        PluginDO pluginDO = pluginMapper.selectByName(pluginName);
        return selectorMapper.findByNameAndPluginId(name, pluginDO.getId());
    }
    
    @Override
    public SelectorData buildByName(final String name) {
        return buildSelectorData(selectorMapper.selectByName(name));
    }
    
    /**
     * Build by name selector data.
     *
     * @param name the name
     * @param pluginName the plugin name
     * @return the selector data
     */
    @Override
    public SelectorData buildByName(final String name, final String pluginName) {
        return buildSelectorData(findByNameAndPluginName(name, pluginName));
    }
    
    /**
     * find page of selector by query.
     *
     * @param selectorQuery {@linkplain SelectorQuery}
     * @return {@linkplain CommonPager}
     */
    @Override
    @DataPermission(dataType = AdminConstants.DATA_PERMISSION_SELECTOR)
    @Pageable
    public CommonPager<SelectorVO> listByPage(final SelectorQuery selectorQuery) {
        return PageResultUtils.result(selectorQuery.getPageParameter(), () -> selectorMapper.selectByQuery(selectorQuery)
                .stream()
                .map(SelectorVO::buildSelectorVO)
                .collect(Collectors.toList()));
    }

    @Override
    public List<SelectorData> findByPluginId(final String pluginId) {
        return selectorMapper.findByPluginId(pluginId)
                .stream()
                .map(this::buildSelectorData)
                .collect(Collectors.toList());
    }

    @Override
    public List<SelectorData> listAll() {
        return selectorMapper.selectAll()
                .stream()
                .filter(Objects::nonNull)
                .map(this::buildSelectorData)
                .collect(Collectors.toList());
    }

    private void publishEvent(final SelectorDO selectorDO, final List<SelectorConditionDTO> selectorConditionDTOs) {
        PluginDO pluginDO = pluginMapper.selectById(selectorDO.getPluginId());
        List<ConditionData> conditionDataList =
                selectorConditionDTOs.stream().map(ConditionTransfer.INSTANCE::mapToSelectorDTO).collect(Collectors.toList());
        // publish change event.
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.SELECTOR, DataEventTypeEnum.UPDATE,
                Collections.singletonList(SelectorDO.transFrom(selectorDO, pluginDO.getName(), conditionDataList))));
    }

    private SelectorData buildSelectorData(final SelectorDO selectorDO) {
        // find conditions
        List<ConditionData> conditionDataList = ConditionTransfer.INSTANCE.mapToSelectorDOS(
                selectorConditionMapper.selectByQuery(new SelectorConditionQuery(selectorDO.getId())));
        PluginDO pluginDO = pluginMapper.selectById(selectorDO.getPluginId());
        if (Objects.isNull(pluginDO)) {
            return null;
        }
        return SelectorDO.transFrom(selectorDO, pluginDO.getName(), conditionDataList);
    }

    private void updateDivideUpstream(final SelectorDO selectorDO) {
        String selectorName = selectorDO.getName();
        PluginDO pluginDO = pluginMapper.selectById(selectorDO.getPluginId());
        List<DivideUpstream> existDivideUpstreams = null;
        if (PluginEnum.SPRING_CLOUD.getName().equals(pluginDO.getName())) {
            if (Objects.nonNull(selectorDO.getHandle())) {
                SpringCloudSelectorHandle springCloudSelectorHandle = GsonUtils.getInstance()
                        .fromJson(selectorDO.getHandle(), SpringCloudSelectorHandle.class);
                existDivideUpstreams = springCloudSelectorHandle.getDivideUpstreams();
            }
        } else if (PluginEnum.DIVIDE.getName().equals(pluginDO.getName())) {
            String handle = selectorDO.getHandle();
            if (StringUtils.isNotBlank(handle)) {
                existDivideUpstreams = GsonUtils.getInstance().fromList(handle, DivideUpstream.class);
            }
        }
        if (CollectionUtils.isNotEmpty(existDivideUpstreams)) {
            upstreamCheckService.replace(selectorName, CommonUpstreamUtils.convertCommonUpstreamList(existDivideUpstreams));
        }
    }

    private String registerSelector(final String contextPath, final String pluginName, final String selectorHandler) {
        SelectorDTO selectorDTO = buildSelectorDTO(contextPath, pluginMapper.selectByName(pluginName).getId());
        selectorDTO.setHandle(selectorHandler);
        return registerDefault(selectorDTO);
    }

    private SelectorDTO buildSelectorDTO(final String contextPath, final String pluginId) {
        SelectorDTO selectorDTO = buildDefaultSelectorDTO(contextPath);
        selectorDTO.setPluginId(pluginId);
        selectorDTO.setSelectorConditions(buildDefaultSelectorConditionDTO(contextPath));
        return selectorDTO;
    }

    private SelectorDTO buildDefaultSelectorDTO(final String name) {
        return SelectorDTO.builder()
                .name(name)
                .type(SelectorTypeEnum.CUSTOM_FLOW.getCode())
                .matchMode(MatchModeEnum.AND.getCode())
                .enabled(Boolean.TRUE)
                .loged(Boolean.TRUE)
                .continued(Boolean.TRUE)
                .sort(1)
                .build();
    }

    private List<SelectorConditionDTO> buildDefaultSelectorConditionDTO(final String contextPath) {
        SelectorConditionDTO selectorConditionDTO = new SelectorConditionDTO();
        selectorConditionDTO.setParamType(ParamTypeEnum.URI.getName());
        selectorConditionDTO.setParamName("/");
        selectorConditionDTO.setOperator(OperatorEnum.MATCH.getAlias());
        selectorConditionDTO.setParamValue(contextPath + "/**");
        return Collections.singletonList(selectorConditionDTO);
    }
}
