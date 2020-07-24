/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl;

import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.VfsRoot;
import org.gradle.internal.snapshot.impl.DefaultVfsRoot;
import org.gradle.internal.vfs.impl.SnapshotCollectingDiffListener;

import java.util.function.Predicate;

public class NotifyingUpdateFunctionRunner implements SnapshotHierarchy.UpdateFunctionRunner {

    private final Predicate<String> watchFilter;
    private ErrorHandlingDiffPublisher errorHandlingDiffPublisher;

    public NotifyingUpdateFunctionRunner(Predicate<String> watchFilter) {
        this.watchFilter = watchFilter;
    }

    public void setSnapshotDiffListener(VfsRoot.SnapshotDiffListener snapshotDiffListener, ErrorHandler errorHandler) {
        this.errorHandlingDiffPublisher = new ErrorHandlingDiffPublisher(snapshotDiffListener, errorHandler);
    }

    public void stopListening() {
        errorHandlingDiffPublisher = null;
    }

    @Override
    public SnapshotHierarchy runUpdateFunction(SnapshotHierarchy.UpdateFunction updateFunction, SnapshotHierarchy root) {
        ErrorHandlingDiffPublisher currentErrorHandlingDiffPublisher = errorHandlingDiffPublisher;
        if (currentErrorHandlingDiffPublisher == null) {
            return updateFunction.update(root, SnapshotHierarchy.NodeDiffListener.NOOP);
        }

        SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener(watchFilter);
        SnapshotHierarchy newRoot = updateFunction.update(root, diffListener);
        return currentErrorHandlingDiffPublisher.publishSnapshotDiff(diffListener, newRoot);
    }

    public interface ErrorHandler {
        void handleErrors(VfsRoot currentRoot, Runnable runnable);
    }

    private static class ErrorHandlingDiffPublisher {
        private final VfsRoot.SnapshotDiffListener diffListener;
        private final ErrorHandler errorHandler;

        public ErrorHandlingDiffPublisher(VfsRoot.SnapshotDiffListener diffListener, ErrorHandler errorHandler) {
            this.diffListener = diffListener;
            this.errorHandler = errorHandler;
        }

        public SnapshotHierarchy publishSnapshotDiff(SnapshotCollectingDiffListener collectedDiff, SnapshotHierarchy newRoot) {
            DefaultVfsRoot vfsRoot = new DefaultVfsRoot(newRoot, SnapshotHierarchy.UpdateFunctionRunner.WITHOUT_LISTENERS);
            errorHandler.handleErrors(vfsRoot, () -> collectedDiff.publishSnapshotDiff(diffListener));
            return vfsRoot.getDelegate();
        }
    }
}