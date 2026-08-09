import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import type { ChartMetric } from '../../types/dashboard';
import { Card } from '../ui/Card';

const colors = ['#46b3c7', '#74a892', '#c7a564', '#8ea6c8', '#b87373'];

export function BarPanel({ title, data }: { title: string; data: ChartMetric[] }) {
  return (
    <Card className="h-80">
      <h3 className="mb-4 text-sm font-semibold text-slate-100">{title}</h3>
      <ResponsiveContainer width="100%" height="88%">
        <BarChart data={data}>
          <CartesianGrid stroke="#1f2d38" strokeDasharray="3 3" />
          <XAxis dataKey="label" stroke="#64748b" fontSize={12} />
          <YAxis stroke="#64748b" fontSize={12} />
          <Tooltip contentStyle={{ background: '#101820', border: '1px solid #243543', borderRadius: 8 }} />
          <Bar dataKey="value" radius={[4, 4, 0, 0]} fill="#46b3c7" />
        </BarChart>
      </ResponsiveContainer>
    </Card>
  );
}

export function PiePanel({ title, data }: { title: string; data: ChartMetric[] }) {
  return (
    <Card className="h-80">
      <h3 className="mb-4 text-sm font-semibold text-slate-100">{title}</h3>
      <ResponsiveContainer width="100%" height="88%">
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="label" innerRadius={58} outerRadius={92} paddingAngle={3}>
            {data.map((_, index) => <Cell key={index} fill={colors[index % colors.length]} />)}
          </Pie>
          <Tooltip contentStyle={{ background: '#101820', border: '1px solid #243543', borderRadius: 8 }} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </Card>
  );
}

export function LinePanel({ title, data }: { title: string; data: ChartMetric[] }) {
  return (
    <Card className="h-80">
      <h3 className="mb-4 text-sm font-semibold text-slate-100">{title}</h3>
      <ResponsiveContainer width="100%" height="88%">
        <LineChart data={data}>
          <CartesianGrid stroke="#1f2d38" strokeDasharray="3 3" />
          <XAxis dataKey="label" stroke="#64748b" fontSize={12} />
          <YAxis stroke="#64748b" fontSize={12} />
          <Tooltip contentStyle={{ background: '#101820', border: '1px solid #243543', borderRadius: 8 }} />
          <Line type="monotone" dataKey="value" stroke="#74a892" strokeWidth={3} dot={{ r: 3 }} />
        </LineChart>
      </ResponsiveContainer>
    </Card>
  );
}

export function AreaPanel({ title, data }: { title: string; data: ChartMetric[] }) {
  return (
    <Card className="h-80">
      <h3 className="mb-4 text-sm font-semibold text-slate-100">{title}</h3>
      <ResponsiveContainer width="100%" height="88%">
        <AreaChart data={data}>
          <defs>
            <linearGradient id="evidenceArea" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#46b3c7" stopOpacity={0.55} />
              <stop offset="95%" stopColor="#46b3c7" stopOpacity={0.03} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="#1f2d38" strokeDasharray="3 3" />
          <XAxis dataKey="label" stroke="#64748b" fontSize={12} />
          <YAxis stroke="#64748b" fontSize={12} />
          <Tooltip contentStyle={{ background: '#101820', border: '1px solid #243543', borderRadius: 8 }} />
          <Area type="monotone" dataKey="value" stroke="#46b3c7" fill="url(#evidenceArea)" strokeWidth={2} />
        </AreaChart>
      </ResponsiveContainer>
    </Card>
  );
}

export function HorizontalBarPanel({ title, data }: { title: string; data: ChartMetric[] }) {
  return (
    <Card className="h-80">
      <h3 className="mb-4 text-sm font-semibold text-slate-100">{title}</h3>
      <ResponsiveContainer width="100%" height="88%">
        <BarChart data={data} layout="vertical" margin={{ left: 16 }}>
          <CartesianGrid stroke="#1f2d38" strokeDasharray="3 3" />
          <XAxis type="number" stroke="#64748b" fontSize={12} />
          <YAxis type="category" dataKey="label" width={120} stroke="#64748b" fontSize={12} />
          <Tooltip contentStyle={{ background: '#101820', border: '1px solid #243543', borderRadius: 8 }} />
          <Bar dataKey="value" radius={[0, 4, 4, 0]} fill="#74a892" />
        </BarChart>
      </ResponsiveContainer>
    </Card>
  );
}
