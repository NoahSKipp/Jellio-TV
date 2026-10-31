package com.jellio.tv.data.syncplay

import com.jellio.tv.data.model.SyncPlayGroupDto

internal sealed interface ReconcileAction {
    data object None : ReconcileAction
    data object Reset : ReconcileAction
    data class Join(val group: SyncPlayGroupDto) : ReconcileAction
}

// What to do about group membership, given the server's group list and
// what this TV believes. Group lists only carry user names, so "still in
// it" means this account's name is among the group's participants.
internal fun decideReconcile(
    groups: List<SyncPlayGroupDto>,
    storedGroupId: String?,
    current: SyncGroup?,
    myUserName: String,
    pendingCreateName: String?,
    leaveGraceActive: Boolean,
): ReconcileAction {
    var mine = storedGroupId?.let { id -> groups.firstOrNull { SyncPlayManager.sameId(it.GroupId, id) } }
    // A group this TV just created whose GroupJoined push went missing.
    if (mine == null && pendingCreateName != null) {
        mine = groups.firstOrNull { it.GroupName == pendingCreateName && myUserName in it.Participants }
    }
    if (mine != null && leaveGraceActive) return ReconcileAction.None
    if (mine != null && (current == null || !SyncPlayManager.sameId(current.groupId, mine.GroupId))) {
        return ReconcileAction.Join(mine)
    }
    if (current == null) return ReconcileAction.None
    val live = groups.firstOrNull { SyncPlayManager.sameId(it.GroupId, current.groupId) }
        // The group itself is gone and the push saying so never arrived.
        ?: return ReconcileAction.Reset
    if (myUserName.isEmpty() || myUserName in live.Participants) return ReconcileAction.None
    // The server dropped this TV (its session ended with the socket) and no
    // push said so. Rejoining would pull this account's other device out of
    // a group it is watching in, so only rejoin when it is in none.
    if (leaveGraceActive || groups.any { myUserName in it.Participants }) return ReconcileAction.Reset
    return ReconcileAction.Join(live)
}
