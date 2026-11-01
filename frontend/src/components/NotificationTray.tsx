import { BellRing, Check, CheckCheck, X } from 'lucide-react';
import { useEffect, useRef } from 'react';

import { formatDateTime, formatRupees } from '../lib/format';
import type { Notification } from '../types/api';
import { Badge } from './Badges';
import { EmptyState } from './EmptyState';

export function NotificationTray({
  notifications,
  acknowledged,
  onAcknowledge,
  onClose,
}: {
  notifications: Notification[];
  acknowledged: Set<string>;
  onAcknowledge: (ids: string[]) => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  const unread = notifications.filter((item) => !acknowledged.has(item.id));

  return (
    <dialog
      ref={dialog}
      onCancel={onClose}
      onClick={(event) => {
        if (event.target === dialog.current) {
          onClose();
        }
      }}
      aria-labelledby="tray-title"
      className="animate-slide-in m-0 ml-auto h-dvh max-h-none w-full max-w-md border-l border-slate-200 bg-slate-50 p-0 text-slate-900 shadow-xl backdrop:bg-slate-900/30"
    >
      <div className="flex h-full flex-col">
        <div className="flex items-center justify-between gap-2 border-b border-slate-200 bg-white px-4 py-3">
          <h2 id="tray-title" className="font-semibold">
            Notifications
          </h2>
          <div className="flex items-center gap-1">
            {unread.length > 0 && (
              <button
                type="button"
                onClick={() => onAcknowledge(unread.map((item) => item.id))}
                className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50"
              >
                <CheckCheck aria-hidden className="size-4" />
                Acknowledge all
              </button>
            )}
            <button
              type="button"
              aria-label="Close notifications"
              onClick={onClose}
              className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100"
            >
              <X aria-hidden className="size-5" />
            </button>
          </div>
        </div>

        <div className="flex-1 space-y-2 overflow-y-auto p-3">
          {notifications.length === 0 && (
            <EmptyState icon={BellRing} title="No notifications yet" hint="You'll see an entry here when one of your alerts triggers." />
          )}
          {notifications.map((item) => {
            const done = acknowledged.has(item.id);
            return (
              <article
                key={item.id}
                className={`rounded-xl border bg-white p-3 ${done ? 'border-slate-200' : 'border-blue-200 ring-1 ring-blue-100'}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="font-semibold">{item.symbol ?? 'Price alert'}</p>
                  {done ? <Badge tone="slate">Acknowledged</Badge> : <Badge tone="blue">New</Badge>}
                </div>
                <p className="mt-1 text-sm text-slate-500">{item.message}</p>
                <div className="mt-2 flex items-end justify-between gap-2">
                  <div>
                    <p className="text-xs text-slate-500">Hit price</p>
                    <p className="font-mono text-sm font-medium">{formatRupees(item.triggeredPrice)}</p>
                    <p className="mt-1 text-xs text-slate-500">{formatDateTime(item.createdAt)}</p>
                  </div>
                  {!done && (
                    <button
                      type="button"
                      onClick={() => onAcknowledge([item.id])}
                      className="flex items-center gap-1 rounded-lg border border-slate-200 px-2 py-1 text-xs font-medium hover:bg-slate-50"
                    >
                      <Check aria-hidden className="size-3.5" />
                      Acknowledge
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      </div>
    </dialog>
  );
}
