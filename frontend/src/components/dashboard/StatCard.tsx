import type { LucideIcon } from 'lucide-react';
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import { Card } from '../ui/Card';

const colorStyles: Record<string, { ring: string; text: string; badge: string }> = {
  cyan: { ring: 'border-cyanline/25 bg-cyanline/10', text: 'text-cyanline', badge: 'bg-cyanline/10 text-cyanline' },
  blue: { ring: 'border-blue-400/25 bg-blue-400/10', text: 'text-blue-300', badge: 'bg-blue-400/10 text-blue-300' },
  green: { ring: 'border-emerald-400/25 bg-emerald-400/10', text: 'text-emerald-300', badge: 'bg-emerald-400/10 text-emerald-300' },
  red: { ring: 'border-rose-400/25 bg-rose-400/10', text: 'text-rose-300', badge: 'bg-rose-400/10 text-rose-300' },
  slate: { ring: 'border-slate-400/25 bg-slate-400/10', text: 'text-slate-300', badge: 'bg-slate-400/10 text-slate-300' },
  amber: { ring: 'border-amber-400/25 bg-amber-400/10', text: 'text-amber-300', badge: 'bg-amber-400/10 text-amber-300' },
  violet: { ring: 'border-violet-400/25 bg-violet-400/10', text: 'text-violet-300', badge: 'bg-violet-400/10 text-violet-300' },
  teal: { ring: 'border-teal-400/25 bg-teal-400/10', text: 'text-teal-300', badge: 'bg-teal-400/10 text-teal-300' },
  orange: { ring: 'border-orange-400/25 bg-orange-400/10', text: 'text-orange-300', badge: 'bg-orange-400/10 text-orange-300' },
  emerald: { ring: 'border-emerald-400/25 bg-emerald-400/10', text: 'text-emerald-300', badge: 'bg-emerald-400/10 text-emerald-300' }
};

export function StatCard({
  label,
  value,
  icon: Icon,
  detail,
  percentageChange,
  trend,
  color
}: {
  label: string;
  value: string | number;
  icon: LucideIcon;
  detail: string;
  percentageChange: number;
  trend: 'up' | 'down' | 'flat';
  color: string;
}) {
  const styles = colorStyles[color] ?? colorStyles.cyan;
  const TrendIcon = trend === 'up' ? ArrowUpRight : trend === 'down' ? ArrowDownRight : Minus;
  const trendLabel = trend === 'up' ? 'Increasing' : trend === 'down' ? 'Decreasing' : 'Stable';

  return (
    <Card className="relative overflow-hidden">
      <div className={`absolute right-0 top-0 h-20 w-20 translate-x-8 -translate-y-8 rounded-full blur-2xl ${styles.badge}`} />
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm text-slate-400">{label}</p>
          <p className="mt-3 text-3xl font-semibold text-white">{value}</p>
          <div className="mt-2 flex items-center gap-2">
            <span className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium ${styles.badge}`}>
              <TrendIcon className="h-3.5 w-3.5" />
              {Math.abs(percentageChange).toFixed(1)}%
            </span>
            <span className="text-xs text-slate-500">{trendLabel}</span>
          </div>
          <p className="mt-2 text-xs text-slate-500">{detail}</p>
        </div>
        <div className={`rounded-md border p-2 ${styles.ring} ${styles.text}`}>
          <Icon className="h-5 w-5" />
        </div>
      </div>
    </Card>
  );
}
