/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Classes which represent the XML Benchmark configuration of OptaPlanner Benchmark.
 * <p>
 * The XML Benchmark configuration is backwards compatible for all elements,
 * except for elements that require the use of non public API classes.
 */
@javax.xml.bind.annotation.XmlSchema(
        namespace = PlannerBenchmarkConfig.XML_NAMESPACE,
        elementFormDefault = XmlNsForm.QUALIFIED,
        xmlns = {
                @XmlNs(namespaceURI = SolverConfig.XML_NAMESPACE, prefix = PlannerBenchmarkConfig.SOLVER_NAMESPACE_PREFIX)
        })
package org.optaplanner.benchmark.config;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;

import org.optaplanner.core.config.solver.SolverConfig;
