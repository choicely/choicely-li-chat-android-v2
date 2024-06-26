/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.services;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.voip.groupcall.GroupCallDescription;
import ch.threema.base.ProgressListener;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.connection.MessageTooLongException;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage;
import ch.threema.localcrypto.MasterKey;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.DisplayTag;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;
import ch.threema.storage.models.data.status.GroupCallStatusDataModel;
import ch.threema.storage.models.data.status.GroupStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

/**
 * Handling methods for messages
 */
public interface MessageService {
	int FILTER_CHATS = 1;
	int FILTER_GROUPS = 1<<1;
	int FILTER_INCLUDE_ARCHIVED = 1<<2;
	int FILTER_STARRED_ONLY = 1<<3;

	@IntDef(
		flag=true,
		value={
			FILTER_CHATS,
			FILTER_GROUPS,
			FILTER_INCLUDE_ARCHIVED,
			FILTER_STARRED_ONLY
		}
	)
	@Retention(RetentionPolicy.SOURCE)
	@interface MessageFilterFlags{}

	interface CompletionHandler {
		void sendComplete(AbstractMessageModel messageModel);
		void sendQueued(AbstractMessageModel messageModel);
		void sendError(int reason);
	}

	interface MessageFilter {
		/**
		 * Max number of messages that are returned with a response
		 */
		long getPageSize();

		/**
		 * If this returns a non-null value, then only messages with a message id smaller than the
		 * reference id will be returned.
		 */
		Integer getPageReferenceId();

		boolean withStatusMessages();
		boolean withUnsaved();
		boolean onlyUnread();
		boolean onlyDownloaded();
		MessageType[] types();
		@MessageContentsType int[] contentTypes();
		/* Messages can be tagged with a star or other attributes that affect how they are displayed.
		If the implementation returns an array of tags, the result will be filtered to contain only messages that have one or more of the specified tags set.
		If this method returns null, no filtering for display tags will be performed */
		@DisplayTag
		int[] displayTags();
	}

	public class MessageString {
		String message;
		String rawMessage;

		public MessageString(String message) {
			this.message = message;
			this.rawMessage = message;
		}

		MessageString(String message, String rawMessage) {
			this.message = message;
			this.rawMessage = rawMessage;
		}

		public String getMessage() {
			return message;
		}

		public String getRawMessage() {
			return rawMessage;
		}
	}

	/**
	 *
	 * @deprecated use createStatusMessage new style
	 */
	@Deprecated
	AbstractMessageModel createStatusMessage(String statusMessage, MessageReceiver receiver);

	AbstractMessageModel createVoipStatus(VoipStatusDataModel data,
										  MessageReceiver receiver,
										  boolean isOutbox,
										  boolean isRead);

	AbstractMessageModel createGroupCallStatus(@NonNull GroupCallStatusDataModel data,
											   @NonNull MessageReceiver receiver,
											   @Nullable ContactModel contactModel,
											   @Nullable GroupCallDescription call,
											   boolean isOutbox,
											   Date postedDate);

	AbstractMessageModel createForwardSecurityStatus(
		@NonNull MessageReceiver receiver,
		@ForwardSecurityStatusDataModel.ForwardSecurityStatusType int type,
		int quantity,
		@Nullable String staticText);

	/**
	 * Create and save a group status message.
	 *
	 * @param receiver     the receiver
	 * @param type         the type
	 * @param identity     the identity that will be included in the message (needed for
	 *                     MEMBER_ADDED, MEMBER_LEFT, MEMBER_KICKED, FIRST_VOTE, and RECEIVED_VOTE)
	 * @param ballotName   the name of the ballot (needed for FIRST_VOTE, MODIFIED_VOTE,
	 *                     RECEIVED_VOTE, and VOTES_COMPLETE)
	 * @param newGroupName the new group name (needed for RENAMED)
	 * @return the group status message model
	 */
	AbstractMessageModel createGroupStatus(
		@NonNull GroupMessageReceiver receiver,
		@NonNull GroupStatusDataModel.GroupStatusType type,
		@Nullable String identity,
		@Nullable String ballotName,
		@Nullable String newGroupName
	);

    AbstractMessageModel sendText(String message, MessageReceiver receiver) throws Exception;
	AbstractMessageModel sendLocation(@NonNull Location location, String poiName, MessageReceiver receiver, CompletionHandler completionHandler) throws ThreemaException, IOException;

	String getCorrelationId();

	@AnyThread
	void sendMediaAsync(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers);
	@AnyThread
	void sendMediaAsync(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers, @Nullable MessageServiceImpl.SendResultListener sendResultListener);

	@AnyThread
	void sendMediaSingleThread(
		@NonNull List<MediaItem> mediaItems,
		@NonNull List<MessageReceiver> messageReceivers
	);

	@WorkerThread
	AbstractMessageModel sendMedia(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers, @Nullable MessageServiceImpl.SendResultListener sendResultListener);

	@WorkerThread
	boolean sendUserAcknowledgement(@NonNull AbstractMessageModel messageModel, boolean markAsRead);

	/**
	 * Send the profile picture to the receiver of the message if the conditions are met to send it.
	 *
	 * @param message the message that may trigger sending the profile picture
	 */
	void executeProfilePictureDistribution(@NonNull AbstractMessage message);

	/**
	 * Send the profile picture to the given contact. This method does not check if it should send
	 * the profile picture according to the user profile distribution rules. If there is no profile
	 * picture set, then a contact delete photo message is sent.
	 *
	 * @param contactModel the contact the photo is sent to
	 * @return true if the profile picture has been sent successfully, false otherwise
	 */
	boolean sendProfilePicture(@NonNull ContactModel contactModel);

	void resendMessage(AbstractMessageModel messageModel, MessageReceiver receiver, CompletionHandler completionHandler) throws Exception;

	AbstractMessageModel sendBallotMessage(BallotModel ballotModel) throws MessageTooLongException;

	@WorkerThread
	boolean sendUserDecline(@NonNull AbstractMessageModel messageModel, boolean markAsRead);

	void updateMessageState(@NonNull final MessageId apiMessageId, MessageState state, @NonNull DeliveryReceiptMessage stateMessage);
	void updateGroupMessageState(@NonNull final MessageId apiMessageId, @NonNull MessageState state, @NonNull GroupDeliveryReceiptMessage stateMessage);
	@Nullable AbstractMessageModel updateMessageStateForOutgoingMessage(@NonNull final MessageId apiMessageId, @NonNull MessageState state, @Nullable Date stateDate, @NonNull String recipientIdentity);
	boolean markAsRead(AbstractMessageModel message, boolean silent) throws ThreemaException;

	@WorkerThread
	boolean markAsConsumed(AbstractMessageModel message) throws ThreemaException;

	void remove(AbstractMessageModel messageModel);

	/**
	 * if silent is true, no event will be fired on delete
	 */
	void remove(AbstractMessageModel messageModel, boolean silent);

	boolean processIncomingContactMessage(AbstractMessage message) throws Exception;
	boolean processIncomingGroupMessage(AbstractGroupMessage message) throws Exception;

	@WorkerThread
	List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver, MessageFilter messageFilter, boolean appendUnreadMessage);
	@WorkerThread
	List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver, MessageFilter messageFilter);
	@WorkerThread
	List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver);
	List<AbstractMessageModel> getMessageForBallot(BallotModel ballotModel);

	MessageModel getContactMessageModel(final Integer id, boolean lazy);
	GroupMessageModel getGroupMessageModel(final Integer id, boolean lazy);
	DistributionListMessageModel getDistributionListMessageModel(final Integer id, boolean lazy);

	MessageString getMessageString(AbstractMessageModel messageModel, int maxLength);
	MessageString getMessageString(AbstractMessageModel messageModel, int maxLength, boolean withPrefix);

	void saveIncomingServerMessage(ServerMessageModel msg);

	boolean downloadMediaMessage(AbstractMessageModel mediaMessageModel, ProgressListener progressListener) throws Exception;
	boolean cancelMessageDownload(AbstractMessageModel messageModel);
	void cancelMessageUpload(AbstractMessageModel messageModel);

	/**
	 * Get all messages in any chat that match the specified criteria - excluding distribution lists
	 * @param queryString Substring to match or null to match all messages
	 * @param filterFlags @MessageFilterFlags for this query
	 * @param sortAscending Date sort order of results. true = oldest messages first, false = newest messages first
	 * @return A list of matching message models
	 */
	List<AbstractMessageModel> getMessagesForText(String queryString, @MessageService.MessageFilterFlags int filterFlags, boolean sortAscending);


	/**
	 * Remove the "star" display tag from all messages
	 * @return number of affected messages
	 */
	@WorkerThread
	int unstarAllMessages();

	@WorkerThread
	long countStarredMessages() throws SQLiteException;

	void saveMessageQueueAsync();
	void saveMessageQueue(@NonNull MasterKey masterKey);

	void removeAll() throws SQLException, IOException, ThreemaException;
	void save(AbstractMessageModel messageModel);

	void markConversationAsRead(MessageReceiver messageReceiver, NotificationService notificationService);

	/**
	 * count all message records (normal, group and distribution lists)
	 */
	long getTotalMessageCount();

	boolean shareMediaMessages(Context context, ArrayList<AbstractMessageModel> models, ArrayList<Uri> shareFileUris, String caption);
	boolean viewMediaMessage(Context context, AbstractMessageModel model, Uri uri);
	boolean shareTextMessage(Context context, AbstractMessageModel model);
	AbstractMessageModel getMessageModelFromId(int id, String type);
	@Nullable AbstractMessageModel getMessageModelByApiMessageIdAndReceiver(@Nullable String id, @NonNull MessageReceiver messageReceiver);

	void cancelVideoTranscoding(AbstractMessageModel messageModel);

	/**
	 * Create a message receiver for the specified message model
	 * @param messageModel AbstractMessageModel to create a receiver for
	 * @return MessageReceiver
	 * @throws ThreemaException in case no MessageReceiver could be created or the AbstractMessageModel is none of the three possible message types
	 */
	MessageReceiver getMessageReceiver(AbstractMessageModel messageModel) throws ThreemaException;
}
