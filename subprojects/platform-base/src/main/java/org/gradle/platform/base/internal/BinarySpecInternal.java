/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.BinarySpec;

import java.util.Set;

public interface BinarySpecInternal extends BinarySpec {

    /**
     * Adds a pre-configured source set: one that is created as part of another binary or component.
     * This is currently used only by native-testing to add the tested component sources to the test binary.
     */
    void source(LanguageSourceSet source);

    /**
     * Return all language source sets, including any pre-configured source sets added via {@link #source(LanguageSourceSet)}.
     */
    Set<LanguageSourceSet> getAllSources();

    void setBinarySources(FunctionalSourceSet sources);

    void setBuildable(boolean buildable);

    BinaryBuildAbility getBuildAbility();

    boolean isLegacyBinary();
}
