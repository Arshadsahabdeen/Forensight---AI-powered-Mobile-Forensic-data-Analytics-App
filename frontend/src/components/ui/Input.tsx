import type { InputHTMLAttributes } from 'react';

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className="w-full rounded-md border border-slate-700 bg-[#0d151c] px-3 py-2 text-sm text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-cyanline focus:ring-2 focus:ring-cyanline/20"
      {...props}
    />
  );
}
