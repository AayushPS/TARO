package org.Aayush.routing.topology;

import lombok.Value;
import org.Aayush.core.id.IDMapper;

import java.nio.ByteBuffer;

/**
 * Compiled topology buffer plus runtime helpers derived from source inputs.
 */
@Value
public class CompiledTopologyModel {
    ByteBuffer modelBuffer;
    IDMapper nodeIdMapper;
    boolean coordinatesEnabled;
}
