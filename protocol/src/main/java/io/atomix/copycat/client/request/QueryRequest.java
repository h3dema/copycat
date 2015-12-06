/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.copycat.client.request;

import io.atomix.catalyst.buffer.BufferInput;
import io.atomix.catalyst.buffer.BufferOutput;
import io.atomix.catalyst.serializer.SerializeWith;
import io.atomix.catalyst.serializer.Serializer;
import io.atomix.catalyst.util.Assert;
import io.atomix.copycat.client.Operation;
import io.atomix.copycat.client.Query;

import java.util.Objects;

/**
 * Protocol query request.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@SerializeWith(id=197)
public class QueryRequest extends OperationRequest<QueryRequest> {

  /**
   * Returns a new query request builder.
   *
   * @return A new query request builder.
   */
  public static Builder builder() {
    return new Builder(new QueryRequest());
  }

  /**
   * Returns a query request builder for an existing request.
   *
   * @param request The request to build.
   * @return The query request builder.
   * @throws IllegalStateException if request is null
   */
  public static Builder builder(QueryRequest request) {
    return new Builder(request);
  }

  private long index;
  private Query query;

  /**
   * Returns the query index.
   *
   * @return The query index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the query.
   *
   * @return The query.
   */
  public Query query() {
    return query;
  }

  @Override
  public Operation operation() {
    return query;
  }

  @Override
  public void readObject(BufferInput<?> buffer, Serializer serializer) {
    super.readObject(buffer, serializer);
    index = buffer.readLong();
    query = serializer.readObject(buffer);
  }

  @Override
  public void writeObject(BufferOutput<?> buffer, Serializer serializer) {
    super.writeObject(buffer, serializer);
    buffer.writeLong(index);
    serializer.writeObject(query, buffer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), session, sequence, index, query);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof QueryRequest) {
      QueryRequest request = (QueryRequest) object;
      return request.session == session
        && request.sequence == sequence
        && request.query.equals(query);
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format("%s[session=%d, sequence=%d, index=%d, query=%s]", getClass().getSimpleName(), session, sequence, index, query);
  }

  /**
   * Query request builder.
   */
  public static class Builder extends OperationRequest.Builder<Builder, QueryRequest> {
    protected Builder(QueryRequest request) {
      super(request);
    }

    /**
     * Sets the request index.
     *
     * @param index The request index.
     * @return The request builder.
     * @throws IllegalArgumentException if {@code index} is less than {@code 0}
     */
    public Builder withIndex(long index) {
      request.index = Assert.argNot(index, index < 0, "index cannot be less than 0");
      return this;
    }

    /**
     * Sets the request query.
     *
     * @param query The request query.
     * @return The request builder.
     * @throws NullPointerException if {@code query} is null
     */
    public Builder withQuery(Query query) {
      request.query = Assert.notNull(query, "query");
      return this;
    }

    /**
     * @throws IllegalStateException if {@code query} is null
     */
    @Override
    public QueryRequest build() {
      super.build();
      Assert.stateNot(request.index < 0, "index cannot be less than 0");
      Assert.stateNot(request.query == null, "query cannot be null");
      return request;
    }
  }

}
