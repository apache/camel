/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws2.kinesis.dst;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstLedger;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ChildShard;
import software.amazon.awssdk.services.kinesis.model.ExpiredIteratorException;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.InvalidArgumentException;
import software.amazon.awssdk.services.kinesis.model.LimitExceededException;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.SequenceNumberRange;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

/**
 * A fake Amazon Kinesis data stream, written from the Kinesis Data Streams API reference, not from the Camel consumer.
 * Each rule names the page it comes from:
 * <ul>
 * <li>[I] GetShardIterator: https://docs.aws.amazon.com/kinesis/latest/APIReference/API_GetShardIterator.html</li>
 * <li>[G] GetRecords: https://docs.aws.amazon.com/kinesis/latest/APIReference/API_GetRecords.html</li>
 * <li>[L] ListShards: https://docs.aws.amazon.com/kinesis/latest/APIReference/API_ListShards.html</li>
 * <li>[S] SplitShard and MergeShards: https://docs.aws.amazon.com/kinesis/latest/APIReference/API_SplitShard.html,
 * https://docs.aws.amazon.com/kinesis/latest/APIReference/API_MergeShards.html</li>
 * </ul>
 * A shard iterator is a position in a shard, valid for five minutes [I] and usable more than once while it is valid.
 * <p>
 * Not modelled: retention (no record is trimmed, and a closed shard stays listed), hash key ranges and partition key
 * routing (the producer picks an open shard), enhanced fan-out, the per-shard read limits beyond the injected
 * ProvisionedThroughputExceededException, server-side encryption, the asynchronous client, the expiry of a NextToken
 * (300 seconds), shards that close between two pages of a listing (a page lists the shards as they are then), and a
 * stream deleted and created again with the same name.
 * <p>
 * Timing (one-stack rule): every call blocks for its round trip first and then reads the stream (reads take effect at
 * the end of their latency), so a LATEST iterator or a GetRecords answer is not older than the call.
 */
final class FakeKinesis implements KinesisClient {

    static final String STREAM = "dst";
    /** [I] "A shard iterator expires 5 minutes after it is returned to the requester." */
    static final long ITERATOR_LIFETIME_MILLIS = 5 * 60_000L;

    static final class Rec {
        final String shard;
        final int index;
        final String seq;
        final String id;
        final long publishedAt;
        /** The consumers (epochs, see {@link #epoch}) that routed it, in order. */
        final List<Integer> routedIn = new ArrayList<>();
        int completed;

        Rec(String shard, int index, String seq, String id, long publishedAt) {
            this.shard = shard;
            this.index = index;
            this.seq = seq;
            this.id = id;
            this.publishedAt = publishedAt;
        }
    }

    static final class FakeShard {
        final String id;
        final String parent;
        final String adjacentParent;
        final String startSeq;
        final List<Rec> records = new ArrayList<>();
        String endSeq;

        FakeShard(String id, String parent, String adjacentParent, String startSeq) {
            this.id = id;
            this.parent = parent;
            this.adjacentParent = adjacentParent;
            this.startSeq = startSeq;
        }

        boolean closed() {
            return endSeq != null;
        }

        Shard toShard() {
            return Shard.builder().shardId(id).parentShardId(parent).adjacentParentShardId(adjacentParent)
                    .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber(startSeq)
                            .endingSequenceNumber(endSeq).build())
                    .build();
        }
    }

    private record Iterator(String shard, int position, long issuedAt) {
    }

    private static final boolean STRICT_LATEST = Boolean.getBoolean("dst.kinesis.strictLatest");

    private final Dst dst;
    private final DstLedger ledger;
    final Map<String, FakeShard> shards = new LinkedHashMap<>();
    private final Map<String, Iterator> iterators = new HashMap<>();
    private final Map<String, Integer> listTokens = new HashMap<>();
    final List<String> violations = new ArrayList<>();
    /**
     * The records skipped by design: a consumer that starts a shard with a LATEST iterator does not read the records
     * stored before it. Only for the first iterator of a shard in a consumer (an epoch).
     */
    final Set<Rec> skippedByLatest = new HashSet<>();
    private final Set<String> latestStarts = new HashSet<>();
    /** epoch/shard of an iterator that expired, until the next iterator of that shard. */
    private final Set<String> expiredBefore = new HashSet<>();
    /** The shards of the first complete ListShards answer of each epoch, and of the listing in progress. */
    private final Map<Integer, Set<String>> firstLists = new HashMap<>();
    private Set<String> listing = new HashSet<>();
    /** Whether a shard has a checkpoint in the resume cache of the world (none without checkpoints). */
    Predicate<String> hasCheckpoint = shard -> false;
    /** The route of the world counts its consumers: a route start begins a new epoch. */
    int epoch;
    double getRecordsFailRate;
    double getIteratorFailRate;
    double listShardsFailRate;
    boolean faults = true;
    int callsInFlight;
    private long nextSeq = 49_600_000_000_000L;
    private int nextShard;
    private int nextIterator;

    FakeKinesis(Dst dst, DstLedger ledger, int shardCount) {
        this.dst = dst;
        this.ledger = ledger;
        for (int i = 0; i < shardCount; i++) {
            addShard(null, null);
        }
    }

    private FakeShard addShard(String parent, String adjacentParent) {
        String id = String.format("shardId-%012d", nextShard++);
        FakeShard s = new FakeShard(id, parent, adjacentParent, String.valueOf(nextSeq));
        shards.put(id, s);
        return s;
    }

    List<FakeShard> openShards() {
        return shards.values().stream().filter(s -> !s.closed()).toList();
    }

    Rec publish(FakeShard shard, String id) {
        Rec r = new Rec(shard.id, shard.records.size(), String.valueOf(nextSeq++), id, dst.now());
        shard.records.add(r);
        ledger.record("published", shard.id, id);
        return r;
    }

    /** [S] SplitShard: the parent is closed, and two child shards take its writes. */
    void split(FakeShard parent) {
        parent.endSeq = String.valueOf(nextSeq - 1);
        FakeShard a = addShard(parent.id, null);
        FakeShard b = addShard(parent.id, null);
        dst.trace("fake: split " + parent.id + " into " + a.id + " and " + b.id);
    }

    /** [S] MergeShards: both shards are closed, and one child shard takes their writes. */
    void merge(FakeShard shard, FakeShard adjacent) {
        shard.endSeq = String.valueOf(nextSeq - 1);
        adjacent.endSeq = String.valueOf(nextSeq - 1);
        FakeShard child = addShard(shard.id, adjacent.id);
        dst.trace("fake: merge " + shard.id + " and " + adjacent.id + " into " + child.id);
    }

    Rec rec(String shard, String seq) {
        for (Rec r : shards.get(shard).records) {
            if (r.seq.equals(seq)) {
                return r;
            }
        }
        return null;
    }

    private long latency() {
        return dst.chance(0.1) ? dst.between(200, 2000) : dst.between(5, 80);
    }

    private void roundTrip(String what) {
        callsInFlight++;
        try {
            dst.block(latency(), what);
        } finally {
            callsInFlight--;
        }
    }

    private String newIterator(String shard, int position) {
        String it = "it-" + nextIterator++;
        iterators.put(it, new Iterator(shard, position, dst.now()));
        return it;
    }

    @Override
    public GetShardIteratorResponse getShardIterator(GetShardIteratorRequest request) {
        roundTrip("GetShardIterator " + request.shardId() + " " + request.shardIteratorTypeAsString());
        if (!STREAM.equals(request.streamName())) {
            violations.add("GetShardIterator of stream " + request.streamName());
            throw ResourceNotFoundException.builder().message("Stream " + request.streamName() + " not found").build();
        }
        FakeShard shard = shards.get(request.shardId());
        if (shard == null) {
            violations.add("GetShardIterator of an unknown shard " + request.shardId());
            throw ResourceNotFoundException.builder().message("Shard " + request.shardId() + " not found").build();
        }
        if (faults && dst.chance(getIteratorFailRate)) {
            ledger.record("iteratorFault", shard.id, "");
            throw Dst.injected(ProvisionedThroughputExceededException.builder()
                    .message("dst: Rate exceeded for shard " + shard.id).build());
        }
        ShardIteratorType type = request.shardIteratorType();
        boolean afterExpiry = expiredBefore.remove(epoch + "/" + shard.id);
        int position;
        switch (type) {
            case TRIM_HORIZON:
                // no record is trimmed: the oldest record is the first one
                position = 0;
                break;
            case LATEST:
                // [I] "Start reading just after the most recent record in the shard"
                position = shard.records.size();
                // skipped by design when the consumer starts a shard of its first shard list (the first iterator of the
                // shard in an epoch), and also when, right after its iterator of such a shard expired, it gets a LATEST
                // iterator again for that shard and has not routed a record of it yet (the consumer has no position to
                // continue from; -Ddst.kinesis.strictLatest=true excuses only the first). Never for a shard with a
                // checkpoint (the resume strategy starts it after the checkpoint) or a shard created later (read from
                // its start)
                boolean first = latestStarts.add(epoch + "/" + shard.id);
                boolean listedFirst = firstLists.getOrDefault(epoch, Set.of()).contains(shard.id);
                if (listedFirst && !hasCheckpoint.test(shard.id)
                        && (first || !STRICT_LATEST && afterExpiry && !routedFrom(shard))) {
                    for (int i = 0; i < position; i++) {
                        skippedByLatest.add(shard.records.get(i));
                    }
                }
                break;
            case AT_SEQUENCE_NUMBER:
            case AFTER_SEQUENCE_NUMBER:
                position = position(shard, request.startingSequenceNumber());
                if (type == ShardIteratorType.AFTER_SEQUENCE_NUMBER) {
                    position++;
                }
                break;
            case AT_TIMESTAMP:
                position = 0;
                Instant ts = request.timestamp();
                while (position < shard.records.size()
                        && Dst.EPOCH_MILLIS + shard.records.get(position).publishedAt < ts.toEpochMilli()) {
                    position++;
                }
                break;
            default:
                violations.add("GetShardIterator with type " + type);
                throw InvalidArgumentException.builder().message("ShardIteratorType " + type).build();
        }
        // any first iterator of the shard in this epoch, also one of another type
        latestStarts.add(epoch + "/" + shard.id);
        String it = newIterator(shard.id, position);
        dst.trace("fake: iterator " + it + " for " + shard.id + " " + type + " at " + position);
        return GetShardIteratorResponse.builder().shardIterator(it).build();
    }

    /** Whether the consumer of the current epoch has routed a record of the shard. */
    private boolean routedFrom(FakeShard shard) {
        for (Rec r : shard.records) {
            if (r.routedIn.contains(epoch)) {
                return true;
            }
        }
        return false;
    }

    /**
     * [I] AT_SEQUENCE_NUMBER and AFTER_SEQUENCE_NUMBER need the sequence number of a record of that shard;
     * InvalidArgumentException otherwise ("Invalid StartingSequenceNumber").
     */
    private int position(FakeShard shard, String seq) {
        if (seq == null) {
            violations.add("GetShardIterator of " + shard.id + " without a starting sequence number");
            throw InvalidArgumentException.builder().message("StartingSequenceNumber is required").build();
        }
        for (Rec r : shard.records) {
            if (r.seq.equals(seq)) {
                return r.index;
            }
        }
        violations.add("GetShardIterator of " + shard.id + " after sequence number " + seq + ", not a record of it");
        throw InvalidArgumentException.builder()
                .message("Invalid StartingSequenceNumber " + seq + " for shard " + shard.id).build();
    }

    @Override
    public GetRecordsResponse getRecords(GetRecordsRequest request) {
        roundTrip("GetRecords " + request.shardIterator());
        Iterator it = iterators.get(request.shardIterator());
        if (it == null) {
            violations.add("GetRecords with an iterator that was never returned: " + request.shardIterator());
            throw InvalidArgumentException.builder().message("Invalid ShardIterator").build();
        }
        // [G] ExpiredIteratorException: "The provided iterator exceeds the maximum age allowed."
        if (dst.now() - it.issuedAt() >= ITERATOR_LIFETIME_MILLIS) {
            ledger.record("iteratorExpired", it.shard(), request.shardIterator());
            expiredBefore.add(epoch + "/" + it.shard());
            throw ExpiredIteratorException.builder()
                    .message("Iterator expired. The iterator was created at time " + it.issuedAt() + " ms").build();
        }
        if (faults && dst.chance(getRecordsFailRate)) {
            ledger.record("getRecordsFault", it.shard(), request.shardIterator());
            throw Dst.injected(ProvisionedThroughputExceededException.builder()
                    .message("dst: Rate exceeded for shard " + it.shard()).build());
        }
        FakeShard shard = shards.get(it.shard());
        // [G] "The maximum number of records to return. Specify a value of up to 10,000."
        int limit = request.limit() != null ? request.limit() : 10_000;
        if (limit < 1 || limit > 10_000) {
            violations.add("GetRecords with limit " + limit);
            throw InvalidArgumentException.builder().message("Limit " + limit).build();
        }
        int available = shard.records.size() - it.position();
        int n = Math.min(limit, available);
        if (n > 0 && dst.chance(0.2)) {
            // [G] the answer may hold fewer records than the limit, even none, although the shard has more
            n = (int) dst.between(0, n - 1);
        }
        List<Record> records = new ArrayList<>();
        for (int i = it.position(); i < it.position() + n; i++) {
            Rec r = shard.records.get(i);
            records.add(Record.builder().data(SdkBytes.fromUtf8String(r.id)).partitionKey("pk-" + r.id)
                    .sequenceNumber(r.seq).approximateArrivalTimestamp(Instant.ofEpochMilli(Dst.EPOCH_MILLIS + r.publishedAt))
                    .build());
            ledger.record("fetched", shard.id, r.id);
        }
        int next = it.position() + n;
        GetRecordsResponse.Builder answer = GetRecordsResponse.builder().records(records)
                .millisBehindLatest(next >= shard.records.size() ? 0L : dst.now() - shard.records.get(next).publishedAt);
        if (shard.closed() && next >= shard.records.size()) {
            // [G] "If set to null, the shard has been closed and the requested iterator does not return any more data"
            // and the child shards are returned "only when the end of the current shard is reached"
            List<ChildShard> children = new ArrayList<>();
            for (FakeShard s : shards.values()) {
                if (shard.id.equals(s.parent) || shard.id.equals(s.adjacentParent)) {
                    List<String> parents = new ArrayList<>();
                    parents.add(s.parent);
                    if (s.adjacentParent != null) {
                        parents.add(s.adjacentParent);
                    }
                    children.add(ChildShard.builder().shardId(s.id).parentShards(parents).build());
                }
            }
            answer.nextShardIterator(null).childShards(children);
        } else {
            answer.nextShardIterator(newIterator(shard.id, next));
        }
        if (n > 0) {
            dst.trace("fake: GetRecords " + shard.id + " returns " + n + " records from " + it.position());
        }
        return answer.build();
    }

    @Override
    public ListShardsResponse listShards(ListShardsRequest request) {
        roundTrip("ListShards");
        // [L] "If you specify NextToken ... you cannot specify StreamName"
        if (request.nextToken() != null && request.streamName() != null) {
            violations.add("ListShards with both NextToken and StreamName");
            throw InvalidArgumentException.builder().message("NextToken and StreamName cannot be provided together")
                    .build();
        }
        int from;
        if (request.nextToken() == null) {
            listing = new HashSet<>();
        }
        if (request.nextToken() != null) {
            Integer f = listTokens.get(request.nextToken());
            if (f == null) {
                violations.add("ListShards with an unknown NextToken " + request.nextToken());
                throw InvalidArgumentException.builder().message("Invalid NextToken").build();
            }
            from = f;
        } else {
            if (!STREAM.equals(request.streamName())) {
                violations.add("ListShards of stream " + request.streamName());
                throw ResourceNotFoundException.builder().message("Stream " + request.streamName() + " not found")
                        .build();
            }
            from = 0;
        }
        if (faults && dst.chance(listShardsFailRate)) {
            ledger.record("listShardsFault", "s", "");
            throw Dst.injected(LimitExceededException.builder().message("dst: Rate exceeded for ListShards").build());
        }
        List<Shard> all = shards.values().stream().map(FakeShard::toShard).toList();
        // [L] the answer is paginated with NextToken. AWS returns up to 1000 shards per page by default; the seed
        // chooses a smaller page here on purpose, so that the consumer pages through the list
        int page = request.maxResults() != null ? request.maxResults() : (int) dst.between(1, Math.max(1, all.size()));
        int to = Math.min(all.size(), from + page);
        List<Shard> pageShards = all.subList(from, to);
        pageShards.forEach(sh -> listing.add(sh.shardId()));
        ListShardsResponse.Builder answer = ListShardsResponse.builder().shards(pageShards);
        if (to < all.size()) {
            String token = "token-" + listTokens.size();
            listTokens.put(token, to);
            answer.nextToken(token);
        } else {
            firstLists.putIfAbsent(epoch, Set.copyOf(listing));
        }
        return answer.build();
    }

    @Override
    public String serviceName() {
        return "kinesis";
    }

    @Override
    public void close() {
        // nothing to close
    }
}
