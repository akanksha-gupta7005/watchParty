import type { Participant, Role } from '../lib/types';
import { ROLE_LABEL } from '../lib/types';

interface Props {
  participants: Participant[];
  meId: string | undefined;
  iAmHost: boolean;
  onAssignRole: (userId: string, role: Role) => void;
  onRemove: (userId: string) => void;
  onTransferHost: (userId: string) => void;
}

const ASSIGNABLE: Role[] = ['MODERATOR', 'PARTICIPANT', 'VIEWER'];

/** Everyone in the room with their role. The host also gets management controls. */
export default function ParticipantList({
  participants,
  meId,
  iAmHost,
  onAssignRole,
  onRemove,
  onTransferHost,
}: Props) {
  return (
    <div className="card">
      <h3>
        Participants <span className="count">{participants.length}</span>
      </h3>
      <ul className="people">
        {participants.map((p) => {
          const isMe = p.userId === meId;
          return (
            <li key={p.userId}>
              <div className="person-main">
                <span className={p.online ? 'dot on' : 'dot'} title={p.online ? 'Online' : 'Reconnecting...'} />
                <span className="person-name">
                  {p.username}
                  {isMe && <span className="you"> (you)</span>}
                </span>
                <span className={`badge badge-${p.role.toLowerCase()}`}>{ROLE_LABEL[p.role]}</span>
              </div>

              {iAmHost && !isMe && p.role !== 'HOST' && (
                <div className="person-actions">
                  <select
                    value={p.role}
                    aria-label={`Role for ${p.username}`}
                    onChange={(e) => onAssignRole(p.userId, e.target.value as Role)}
                  >
                    {ASSIGNABLE.map((r) => (
                      <option key={r} value={r}>
                        {ROLE_LABEL[r]}
                      </option>
                    ))}
                  </select>
                  <button
                    className="btn small"
                    disabled={!p.online}
                    onClick={() => {
                      if (window.confirm(`Make ${p.username} the host? You will become a moderator.`)) {
                        onTransferHost(p.userId);
                      }
                    }}
                  >
                    Make host
                  </button>
                  <button
                    className="btn small danger"
                    onClick={() => {
                      if (window.confirm(`Remove ${p.username} from the room?`)) onRemove(p.userId);
                    }}
                  >
                    Remove
                  </button>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
