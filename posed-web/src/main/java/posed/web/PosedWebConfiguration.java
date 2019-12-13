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

package posed.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.WebFilter;

import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;

import posed.core.PosedCoreConfiguration;
import reactor.core.publisher.Mono;

/** Configuration needed for the posed.web package. */
@Configuration
@ComponentScan
@Import(PosedCoreConfiguration.class)
public class PosedWebConfiguration {
    /**
     * Creates a configurator to setup the Armeria server.
     * @return a configurator for Armeria server
     */
    @Bean
    public ArmeriaServerConfigurator armeriaServerConfigurator() {
        return builder -> {
            /* Add DocService that enables you to send Thrift and
             * gRPC requests from web browser. */
            builder.serviceUnder("/docs", new DocService());
        };
    }

    /**
     * Creates a {@code WebFilter} that forwards to an index.html.
     * @return a {@code WebFilter} that forwards to an index.html
     */
    @Bean
    public WebFilter indexFilter() {
        return (exchange, chain) -> {
            // If the existing filter doesn't find a resource, then try to fix it.
            return chain.filter(exchange).onErrorResume(t -> {
                String path = exchange.getRequest().getURI().getPath();
                // If it ends with a slash, then assume there is an index.html to try.
                if (path.endsWith("/")) {
                    return chain.filter(exchange.mutate().request(
                            exchange.getRequest().mutate().path(path + "index.html")
                            .build()).build());
                } else {
                    return Mono.error(t);
                }
            });
        };
    }
}
