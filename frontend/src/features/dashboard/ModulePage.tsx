import { Construction, ShieldCheck } from 'lucide-react';
import { motion } from 'framer-motion';
import { Card } from '../../components/ui/Card';

export function ModulePage({ title }: { title: string }) {
  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
      <Card className="min-h-[480px]">
        <div className="flex h-full min-h-[430px] flex-col items-center justify-center text-center">
          <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-lg border border-cyanline/30 bg-cyanline/10">
            <Construction className="h-8 w-8 text-cyanline" />
          </div>
          <h1 className="text-2xl font-semibold text-white">{title}</h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-slate-400">
            This module is architected, routed, and secured for future forensic investigation workflows. Implementation is scheduled for later milestones.
          </p>
          <div className="mt-6 flex items-center gap-2 rounded-md border border-slate-800 bg-white/[0.03] px-3 py-2 text-sm text-slate-300">
            <ShieldCheck className="h-4 w-4 text-signal" />
            Protected enterprise module placeholder
          </div>
        </div>
      </Card>
    </motion.div>
  );
}
