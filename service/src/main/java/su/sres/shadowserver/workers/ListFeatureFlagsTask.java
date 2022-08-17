/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package su.sres.shadowserver.workers;

import su.sres.shadowserver.storage.FeatureFlagsManager;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class ListFeatureFlagsTask extends AbstractFeatureFlagTask {

    public ListFeatureFlagsTask(final FeatureFlagsManager featureFlagsManager) {
        super("list-feature-flags", featureFlagsManager);
    }

    @Override
    public void execute(final Map<String, List<String>> parameters, final PrintWriter out) {
        printFeatureFlags(out);
    }
}
