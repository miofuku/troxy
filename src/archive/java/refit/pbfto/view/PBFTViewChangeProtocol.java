package refit.pbfto.view;


public class PBFTViewChangeProtocol
{

    public static enum State
    {
        VIEW_CHANGE_INITIATED, // View change started locally.
        VIEW_CHANGE_PREPARED,  // VC is prepared at the current shard.
        VIEW_CHANGE_READY,     // All internal VC acceptors prepared and each shard can send its VC.
        VIEW_CHANGE_CONFIRMED, // Received at least f+1 VCs with greater view number.
        VIEW_CHANGE_AGREED,    // Internal coordinator received 2f+1 valid VCs (including own) with same view number.
        NEW_VIEW_READY,        // Shards at the group coordinator can send their NVs.
        NEW_VIEW_CONFIRMED,    // All shards a a new view message are confirmed.
        VIEW_STABLE            // Stable view. If in view change, internal acceptors can switch the view.
    }

}
