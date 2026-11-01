import type { LucideIcon } from 'lucide-react';

export function EmptyState({ icon: Icon, title, hint }: { icon: LucideIcon; title: string; hint: string }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <span className="mb-3 grid size-11 place-items-center rounded-full bg-slate-100">
        <Icon aria-hidden className="size-5 text-slate-500" />
      </span>
      <p className="text-sm font-medium text-slate-900">{title}</p>
      <p className="mt-1 max-w-xs text-sm text-slate-500">{hint}</p>
    </div>
  );
}
