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

package org.apache.shenyu.admin.utils;

import org.apache.shenyu.common.dto.convert.selector.CommonUpstream;
import org.apache.shenyu.common.dto.convert.selector.DivideUpstream;
import org.apache.shenyu.common.dto.convert.selector.DubboUpstream;
import org.apache.shenyu.common.dto.convert.selector.GrpcUpstream;
import org.apache.shenyu.common.dto.convert.selector.TarsUpstream;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Build upstream for rpc plugin.
 */
public class CommonUpstreamUtils {
    
    /**
     * Build divide upstream divide upstream.
     *
     * @param host the host
     * @param port the port
     * @return the divide upstream
     */
    public static DivideUpstream buildDefaultDivideUpstream(final String host, final Integer port) {
        return DivideUpstream.builder().upstreamHost("localhost").protocol("http://").upstreamUrl(buildUrl(host, port)).weight(50).warmup(10).timestamp(System.currentTimeMillis()).build();
    }
    
    /**
     * Build default dubbo upstream dubbo upstream.
     *
     * @param host the host
     * @param port the port
     * @return the dubbo upstream
     */
    public static DubboUpstream buildDefaultDubboUpstream(final String host, final Integer port) {
        return DubboUpstream.builder().upstreamHost("localhost").protocol("dubbo://").upstreamUrl(buildUrl(host, port)).weight(50).warmup(10).timestamp(System.currentTimeMillis()).build();
    }
    
    /**
     * Build default grpc upstream grpc upstream.
     *
     * @param host the host
     * @param port the port
     * @return the grpc upstream
     */
    public static GrpcUpstream buildDefaultGrpcUpstream(final String host, final Integer port) {
        return GrpcUpstream.builder().upstreamUrl(buildUrl(host, port)).weight(50).timestamp(System.currentTimeMillis()).build();
    }
    
    /**
     * Build default tars upstream tars upstream.
     *
     * @param host the host
     * @param port the port
     * @return the tars upstream
     */
    public static TarsUpstream buildDefaultTarsUpstream(final String host, final Integer port) {
        return TarsUpstream.builder().upstreamUrl(buildUrl(host, port)).weight(50).warmup(10).timestamp(System.currentTimeMillis()).build();
    }
    
    /**
     * Convert common upstream list list.
     *
     * @param upstreamList the upstream list
     * @return the list
     */
    public static List<CommonUpstream> convertCommonUpstreamList(final List<? extends CommonUpstream> upstreamList) {
        return upstreamList.stream().map(upstream -> new CommonUpstream(upstream.getProtocol(), upstream.getUpstreamHost(), upstream.getUpstreamUrl())).collect(Collectors.toList());
    }
    
    
    /**
     * Build url string.
     *
     * @param host the host
     * @param port the port
     * @return the string
     */
    public static String buildUrl(final String host, final Integer port) {
        return Optional.of(String.join(":", host, String.valueOf(port))).orElse(null);
    }
}
