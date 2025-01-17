/*
 * Copyright (C) 2019, Scott Dial, All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package posed.grpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.protobuf.services.ProtoReflectionService;
import posed.core.PosedCoreConfiguration;

/** Configuration needed for the posed.core package. */
@Configuration
@ComponentScan
@Import(PosedCoreConfiguration.class)
public class PosedGrpcConfiguration {
    /**
     * Gets a server configurator for the GRPC reflection service.
     * @return a server configurator for the GRPC reflection service
     */
    @Bean
    public ArmeriaServerConfigurator protoReflectionService() {
        return builder -> {
            builder.service(GrpcService.builder()
                    .addService(ProtoReflectionService.newInstance())
                    .autoCompression(true)
                    .enableHttpJsonTranscoding(true)
                    .enableUnframedRequests(true)
                    .supportedSerializationFormats(GrpcSerializationFormats.values())
                    .build());
        };
    }

    /**
     * Gets a server configurator for the PoseGrpcService.
     * @param service instance of PoseGrpcService
     * @return a server configurator for the PoseGrpcService
     */
    @Bean
    @SuppressFBWarnings("OCP_OVERLY_CONCRETE_PARAMETER")
    public ArmeriaServerConfigurator poseServiceGrpc(PoseGrpcService service) {
        return builder -> {
            builder.service(GrpcService.builder()
                    .addService(service)
                    .autoCompression(true)
                    .enableHttpJsonTranscoding(true)
                    .enableUnframedRequests(true)
                    .supportedSerializationFormats(GrpcSerializationFormats.values())
                    .useBlockingTaskExecutor(true)
                    .build());
        };
    }
}
