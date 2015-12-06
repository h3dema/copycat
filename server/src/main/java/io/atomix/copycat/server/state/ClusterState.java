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
package io.atomix.copycat.server.state;

import io.atomix.catalyst.util.Assert;
import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.storage.system.Configuration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Cluster state.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class ClusterState {
  private final ServerState context;
  private final Member member;
  private long index = -1;
  private final Map<Integer, MemberState> membersMap = new HashMap<>();
  private final List<MemberState> members = new ArrayList<>();
  private final Map<CopycatServer.Type, List<MemberState>> memberTypes = new HashMap<>();

  ClusterState(ServerState context, Member member) {
    this.context = Assert.notNull(context, "context");
    this.member = Assert.notNull(member, "member");
  }

  /**
   * Returns the local cluster member.
   *
   * @return The local cluster member.
   */
  public Member getMember() {
    return member;
  }

  /**
   * Returns the remote quorum count.
   *
   * @return The remote quorum count.
   */
  int getQuorum() {
    return (int) Math.floor((getRemoteMemberStates(CopycatServer.Type.ACTIVE).size() + 1) / 2.0) + 1;
  }

  /**
   * Returns the cluster state index.
   *
   * @return The cluster state index.
   */
  long getVersion() {
    return index;
  }

  /**
   * Returns a member by ID.
   *
   * @param id The member ID.
   * @return The member.
   */
  public Member getMember(int id) {
    if (member.id() == id) {
      return member;
    }
    return getRemoteMember(id);
  }

  /**
   * Returns a member by ID.
   *
   * @param id The member ID.
   * @return The member.
   */
  public Member getRemoteMember(int id) {
    MemberState member = membersMap.get(id);
    return member != null ? member.getMember() : null;
  }

  /**
   * Returns a member by ID.
   *
   * @param id The member ID.
   * @return The member state.
   */
  MemberState getRemoteMemberState(int id) {
    return membersMap.get(id);
  }

  /**
   * Returns the current cluster members.
   *
   * @return The current cluster members.
   */
  public List<Member> getMembers() {
    // Add all members to a list. The "members" field is only remote members, so we must separately
    // add the local member to the list if necessary.
    List<Member> members = new ArrayList<>(this.members.size() + 1);
    for (MemberState member : this.members) {
      members.add(member.getMember());
    }

    // If the local member type is null, that indicates it's not a member of the current configuration.
    if (member.type() != null) {
      members.add(member);
    }
    return members;
  }

  /**
   * Returns a list of all remote members.
   *
   * @return A list of all remote members.
   */
  public List<Member> getRemoteMembers() {
    return members.stream().map(MemberState::getMember).collect(Collectors.toList());
  }

  /**
   * Returns a list of all members.
   *
   * @return A list of all members.
   */
  List<MemberState> getRemoteMemberStates() {
    return members;
  }

  /**
   * Returns a list of remote members for the given type.
   *
   * @param type The member type.
   * @return A list of remote members.
   */
  public List<Member> getRemoteMembers(CopycatServer.Type type) {
    return getRemoteMemberStates(type).stream().map(MemberState::getMember).collect(Collectors.toList());
  }

  /**
   * Returns a list of remote member states for the given type.
   *
   * @param type The member type.
   * @return A list of remote member states.
   */
  List<MemberState> getRemoteMemberStates(CopycatServer.Type type) {
    List<MemberState> memberType = memberTypes.get(type);
    return memberType != null ? memberType : Collections.EMPTY_LIST;
  }

  /**
   * Returns a sorted list of remote member states for the given type.
   *
   * @param type The member type.
   * @param comparator A comparator with which to sort the members.
   * @return A sorted list of remote member states.
   */
  List<MemberState> getRemoteMemberStates(CopycatServer.Type type, Comparator<MemberState> comparator) {
    List<MemberState> memberType = getRemoteMemberStates(type);
    Collections.sort(memberType, comparator);
    return memberType;
  }

  /**
   * Configures the cluster state.
   *
   * @param index The cluster state index.
   * @param members The cluster members.
   * @return The cluster state.
   */
  ClusterState configure(long index, Collection<Member> members) {
    if (index <= this.index)
      return this;

    // If the configuration index is less than the currently configured index, ignore it.
    // Configurations can be persisted and applying old configurations can revert newer configurations.
    if (index <= this.index)
      return this;

    // Iterate through members in the new configuration, add any missing members, and update existing members.
    for (Member member : members) {
      if (member.equals(this.member)) {
        this.member.update(member.type()).update(member.clientAddress());
      } else {
        // If the member state doesn't already exist, create it.
        MemberState state = membersMap.get(member.id());
        if (state == null) {
          state = new MemberState(new Member(member.type(), member.serverAddress(), member.clientAddress()));
          state.resetState(context.getLog());
          this.members.add(state);
          membersMap.put(member.id(), state);
        }

        // If the member type has changed, update the member type and reset its state.
        state.getMember().update(member.clientAddress());
        if (state.getMember().type() != member.type()) {
          state.getMember().update(member.type());
          state.resetState(context.getLog());
        }

        // Update the optimized member collections according to the member type.
        for (List<MemberState> memberType : memberTypes.values()) {
          memberType.remove(state);
        }

        if (member.type() != null) {
          List<MemberState> memberType = memberTypes.get(member.type());
          if (memberType == null) {
            memberType = new ArrayList<>();
            memberTypes.put(member.type(), memberType);
          }
          memberType.add(state);
        }
      }
    }

    // If the local member is not part of the configuration, set its type to null.
    if (!members.contains(this.member)) {
      this.member.update(CopycatServer.Type.INACTIVE);
    }

    // Iterate through configured members and remove any that no longer exist in the configuration.
    Iterator<MemberState> iterator = this.members.iterator();
    while (iterator.hasNext()) {
      MemberState member = iterator.next();
      if (!members.contains(member.getMember())) {
        iterator.remove();
        for (List<MemberState> memberType : memberTypes.values()) {
          memberType.remove(member);
        }
        membersMap.remove(member.getMember().id());
      }
    }

    this.index = index;

    // Store the configuration to ensure it can be easily loaded on server restart.
    context.getMetaStore().storeConfiguration(new Configuration(index, members));

    return this;
  }

}
