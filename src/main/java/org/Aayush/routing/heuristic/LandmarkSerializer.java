package org.Aayush.routing.heuristic;

import com.google.flatbuffers.FlatBufferBuilder;
import lombok.experimental.UtilityClass;
import org.Aayush.serialization.flatbuffers.taro.model.Landmark;

import java.util.Objects;

/**
 * FlatBuffers serializer helper for landmark artifacts.
 */
@UtilityClass
public final class LandmarkSerializer {
    /**
     * Serializes landmark artifact into a FlatBuffers `Model.landmarks` vector offset.
     */
    public static int createLandmarksVector(FlatBufferBuilder builder, LandmarkArtifact artifact) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(artifact, "artifact");

        int landmarkCount = artifact.landmarkCount();
        int[] landmarkNodeIds = artifact.landmarkNodeIdsCopy();
        int[] landmarkOffsets = new int[landmarkCount];
        for (int i = 0; i < landmarkCount; i++) {
            int forward = Landmark.createForwardDistancesVector(builder, artifact.forwardDistancesCopy(i));
            int backward = Landmark.createBackwardDistancesVector(builder, artifact.backwardDistancesCopy(i));

            Landmark.startLandmark(builder);
            Landmark.addNodeIdx(builder, landmarkNodeIds[i]);
            Landmark.addForwardDistances(builder, forward);
            Landmark.addBackwardDistances(builder, backward);
            landmarkOffsets[i] = Landmark.endLandmark(builder);
        }

        org.Aayush.serialization.flatbuffers.taro.model.Model.startLandmarksVector(builder, landmarkCount);
        for (int i = landmarkCount - 1; i >= 0; i--) {
            builder.addOffset(landmarkOffsets[i]);
        }
        return builder.endVector();
    }
}
