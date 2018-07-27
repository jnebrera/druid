/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.aggregation.first;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.druid.java.util.common.StringUtils;
import io.druid.query.aggregation.Aggregator;
import io.druid.query.aggregation.BufferAggregator;
import io.druid.query.aggregation.SerializablePairLongString;
import io.druid.query.monomorphicprocessing.RuntimeShapeInspector;
import io.druid.segment.BaseObjectColumnValueSelector;
import io.druid.segment.ColumnSelectorFactory;

import java.nio.ByteBuffer;

@JsonTypeName("stringFirstFold")
public class StringFirstFoldingAggregatorFactory extends StringFirstAggregatorFactory
{
  public StringFirstFoldingAggregatorFactory(
      @JsonProperty("name") String name,
      @JsonProperty("fieldName") final String fieldName,
      @JsonProperty("maxStringBytes") Integer maxStringBytes,
      @JsonProperty("filterNullValues") Boolean filterNullValues
  )
  {
    super(name, fieldName, maxStringBytes, filterNullValues);
  }

  @Override
  public Aggregator factorize(ColumnSelectorFactory metricFactory)
  {
    final BaseObjectColumnValueSelector selector = metricFactory.makeColumnValueSelector(getName());
    return new StringFirstAggregator(null, null, maxStringBytes, filterNullValues)
    {
      @Override
      public void aggregate()
      {
        SerializablePairLongString pair = (SerializablePairLongString) selector.getObject();
        if (pair != null && pair.lhs < firstTime) {
          firstTime = pair.lhs;
          firstValue = pair.rhs;
        }
      }
    };
  }

  @Override
  public BufferAggregator factorizeBuffered(ColumnSelectorFactory metricFactory)
  {
    final BaseObjectColumnValueSelector selector = metricFactory.makeColumnValueSelector(getName());
    return new StringFirstBufferAggregator(null, null, maxStringBytes, filterNullValues)
    {
      @Override
      public void aggregate(ByteBuffer buf, int position)
      {
        SerializablePairLongString pair = (SerializablePairLongString) selector.getObject();

        if (pair != null && pair.lhs != null) {
          ByteBuffer mutationBuffer = buf.duplicate();
          mutationBuffer.position(position);

          long lastTime = mutationBuffer.getLong(position);

          if (pair.lhs < lastTime) {
            mutationBuffer.putLong(position, pair.lhs);

            if (pair.rhs != null) {
              byte[] valueBytes = StringUtils.toUtf8(pair.rhs);

              mutationBuffer.putInt(position + Long.BYTES, valueBytes.length);
              mutationBuffer.position(position + Long.BYTES + Integer.BYTES);
              mutationBuffer.put(valueBytes);
            } else if (!filterNullValues) {
              mutationBuffer.putInt(position + Long.BYTES, 0);
            }
          }
        }
      }

      @Override
      public void inspectRuntimeShape(RuntimeShapeInspector inspector)
      {
        inspector.visit("selector", selector);
      }
    };
  }

}
