/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.util.Arrays;
import java.util.Map;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Encapsulates data required to instantiate a <code>FileInstanceNode</code>.
 */
final public class IntraCaseCommonAttributeSearchResults extends CommonAttributeInstanceNodeGenerator {

    /**
     * Create meta data required to find an abstract file and build a
     * FileInstanceNode.
     *
     * @param objectId id of abstract file to find
     * @param dataSourceName name of datasource where the object is found
     */
    IntraCaseCommonAttributeSearchResults(Long abstractFileReference, Map<Long, AbstractFile> cachedFiles, String dataSource, String caseName) {
        super(abstractFileReference, cachedFiles, dataSource, caseName);
    }

    @Override
    public DisplayableItemNode generateNode() {
        return new IntraCaseCommonAttributeInstanceNode(this.lookupOrCreateAbstractFile(), this.getCaseName(), this.getDataSource());
    }

    @Override
    public DisplayableItemNode[] generateNodes() {
        return Arrays.asList(new IntraCaseCommonAttributeInstanceNode(this.lookupOrCreateAbstractFile(), this.getCaseName(), this.getDataSource())).toArray(new DisplayableItemNode[1]);
    }
}
