package org.schabi.newpipe.info_list;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.StreamDialogDefaultEntry;
import org.schabi.newpipe.util.StreamDialogEntry;
import org.schabi.newpipe.util.external_communication.KoreUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for a {@link StreamInfoItem}.
 * The dialog'S content are actions that can be performed on the {@link StreamInfoItem}.
 * This dialog is mostly used for longpress context menus.
 */
public final class InfoItemDialog {
    /**
     * Ideally, {@link InfoItemDialog} would extend {@link AlertDialog}.
     * However, extending {@link AlertDialog} requires many additional lines
     * and brings more complexity to this class, especially the constructor.
     * To circumvent this, an {@link AlertDialog.Builder} is used in the constructor.
     * Its result is stored in this class variable to allow access via the {@link #show()} method.
     */
    private final AlertDialog dialog;

    private InfoItemDialog(@NonNull final Activity activity,
                           @NonNull final Fragment fragment,
                           @NonNull final StreamInfoItem info,
                           @NonNull final List<StreamDialogEntry> entries) {

        final View bannerView = View.inflate(activity, R.layout.dialog_title, null);
        bannerView.setSelected(true);

        final TextView titleView = bannerView.findViewById(R.id.itemTitleView);
        titleView.setText(info.getName());

        final TextView detailsView = bannerView.findViewById(R.id.itemAdditionalDetails);
        if (info.getUploaderName() != null) {
            detailsView.setText(info.getUploaderName());
            detailsView.setVisibility(View.VISIBLE);
        } else {
            detailsView.setVisibility(View.GONE);
        }

        final String[] items = entries.stream()
                .map(entry -> entry.getString(activity)).toArray(String[]::new);

        final DialogInterface.OnClickListener action = (d, index) ->
            entries.get(index).action.onClick(fragment, info);

        dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(bannerView)
                .setItems(items, action)
                .create();

    }

    public void show() {
        dialog.show();
    }

    /**
     * <p>Builder to generate a {@link InfoItemDialog}.</p>
     * Use {@link #addEntry(StreamDialogDefaultEntry)}
     * and {@link #addAllEntries(StreamDialogDefaultEntry...)} to add options to the dialog.
     * <br>
     * Custom actions for entries can be set using
     * {@link #setAction(StreamDialogDefaultEntry, StreamDialogEntry.StreamDialogEntryAction)}.
     */
    public static class Builder {
        @NonNull private final Activity activity;
        @NonNull private final Context context;
        @NonNull private final StreamInfoItem infoItem;
        @NonNull private final Fragment fragment;
        @NonNull private final List<StreamDialogEntry> entries = new ArrayList<>();
        private final boolean addDefaultEntriesAutomatically;

        /**
         * Create a Builder instance that automatically adds the some default entries
         * at the top and bottom of the dialog.
         * @param activity
         * @param context
         * @param fragment
         * @param infoItem the item for this dialog; all entries and their actions work with
         *                this {@link org.schabi.newpipe.extractor.InfoItem}
         */
        public Builder(@NonNull final Activity activity,
                       @NonNull final Context context,
                       @NonNull final Fragment fragment,
                       @NonNull final StreamInfoItem infoItem) {
            this(activity, context, fragment, infoItem, true);
        }

        /**
         * <p>Create an instance of this Builder.</p>
         *
         * @param activity
         * @param context
         * @param fragment
         * @param infoItem
         * @param addDefaultEntriesAutomatically
         *        whether default entries added with {@link #addDefaultBeginningEntries()}
         *        and {@link #addDefaultEndEntries()} are added automatically when generating
         *        the {@link InfoItemDialog}.
         *        <br/>
         *        Entries added with {@link #addEntry(StreamDialogDefaultEntry)} and
         *        {@link #addAllEntries(StreamDialogDefaultEntry...)} are added in between.
         */
        public Builder(@NonNull final Activity activity,
                       @NonNull final Context context,
                       @NonNull final Fragment fragment,
                       @NonNull final StreamInfoItem infoItem,
                       final boolean addDefaultEntriesAutomatically) {
            this.activity = activity;
            this.context = context;
            this.fragment = fragment;
            this.infoItem = infoItem;
            this.addDefaultEntriesAutomatically = addDefaultEntriesAutomatically;
            if (addDefaultEntriesAutomatically) {
                addDefaultBeginningEntries();
            }
        }

        public void addEntry(@NonNull final StreamDialogDefaultEntry entry) {
            entries.add(entry.toStreamDialogEntry());
        }

        public void addAllEntries(@NonNull final StreamDialogDefaultEntry... newEntries) {
            for (final StreamDialogDefaultEntry entry: newEntries) {
                this.entries.add(entry.toStreamDialogEntry());
            }
        }

        /**
         * <p>Change an entries' action that is called when the entry is selected.</p>
         * <p><strong>Warning:</strong> Only use this method when the entry has been already added.
         * Changing the action of an entry which has not been added to the Builder yet
         * does not have an effect.</p>
         * @param entry the entry to change
         * @param action the action to perform when the entry is selected
         */
        public void setAction(@NonNull final StreamDialogDefaultEntry entry,
                              @NonNull final StreamDialogEntry.StreamDialogEntryAction action) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).resource == entry.resource) {
                    entries.set(i, new StreamDialogEntry(entry.resource, action));
                    return;
                }
            }
        }

        /**
         * Adds {@link StreamDialogDefaultEntry#ENQUEUE} if the player is open and
         * {@link StreamDialogDefaultEntry#ENQUEUE_NEXT} if there are multiple streams
         * in the play queue.
         */
        public void addEnqueueEntriesIfNeeded() {
            if (PlayerHolder.getInstance().isPlayerOpen()) {
                addEntry(StreamDialogDefaultEntry.ENQUEUE);

                if (PlayerHolder.getInstance().getQueueSize() > 1) {
                    addEntry(StreamDialogDefaultEntry.ENQUEUE_NEXT);
                }
            }
        }

        /**
         * Adds the {@link StreamDialogDefaultEntry#START_HERE_ON_BACKGROUND}.
         * If the {@link #infoItem} is not a pure audio (live) stream,
         * {@link StreamDialogDefaultEntry#START_HERE_ON_POPUP} is added, too.
         */
        public void addStartHereEntries() {
            addEntry(StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND);
            if (infoItem.getStreamType() != StreamType.AUDIO_STREAM
                    && infoItem.getStreamType() != StreamType.AUDIO_LIVE_STREAM) {
                addEntry(StreamDialogDefaultEntry.START_HERE_ON_POPUP);
            }
        }

        /**
         * Adds {@link StreamDialogDefaultEntry.MARK_AS_WATCHED} if the watch history is enabled
         * and the stream is not a livestream.
         */
        public void addMarkAsWatchedEntryIfNeeded() {
            final boolean isWatchHistoryEnabled = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.enable_watch_history_key), false);
            if (isWatchHistoryEnabled
                    && infoItem.getStreamType() != StreamType.LIVE_STREAM
                    && infoItem.getStreamType() != StreamType.AUDIO_LIVE_STREAM) {
                addEntry(StreamDialogDefaultEntry.MARK_AS_WATCHED);
            }
        }

        public void addPlayWithKodiEntryIfNeeded() {
            if (KoreUtils.shouldShowPlayWithKodi(context, infoItem.getServiceId())) {
                addEntry(StreamDialogDefaultEntry.PLAY_WITH_KODI);
            }
        }

        /**
         * Add the entries which are usually at the top of the action list.
         * <br/>
         * This method adds the "enqueue" (see {@link #addEnqueueEntriesIfNeeded()})
         * and "start here" (see {@link #addStartHereEntries()} entries.
         */
        public void addDefaultBeginningEntries() {
            addEnqueueEntriesIfNeeded();
            addStartHereEntries();
        }

        /**
         * Add the entries which are usually at the bottom of the action list.
         */
        public void addDefaultEndEntries() {
            addAllEntries(
                    StreamDialogDefaultEntry.APPEND_PLAYLIST,
                    StreamDialogDefaultEntry.SHARE,
                    StreamDialogDefaultEntry.OPEN_IN_BROWSER
            );
            addPlayWithKodiEntryIfNeeded();
            addMarkAsWatchedEntryIfNeeded();
            addEntry(StreamDialogDefaultEntry.SHOW_CHANNEL_DETAILS);
        }

        /**
         * Creates the {@link InfoItemDialog}.
         * @return a new instance of {@link InfoItemDialog}
         */
        public InfoItemDialog create() {
            if (addDefaultEntriesAutomatically) {
                addDefaultEndEntries();
            }
            return new InfoItemDialog(this.activity, this.fragment, this.infoItem, this.entries);
        }
    }
}
