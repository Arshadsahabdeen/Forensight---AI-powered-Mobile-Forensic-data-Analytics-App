import { useQuery } from "@tanstack/react-query";
import {
  Brain,
  CheckCircle2,
  Phone,
  MapPin,
  Users,
  MessageSquare,
  AlertTriangle,
  Loader2
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { analyzeDevice } from "../../services/aiService";

interface Props {
  deviceId: number;
}

export function AIAnalysisPanel({ deviceId }: Props) {

  const {
    data,
    isLoading,
    isError,
    refetch,
    isFetching
  } = useQuery({
    queryKey: ["ai-analysis", deviceId],
    queryFn: () => analyzeDevice(deviceId),
    enabled: false
  });

  if (!data && !isLoading) {
    return (
      <Card>
        <div className="p-8 text-center">

          <Brain
            size={42}
            className="mx-auto mb-4 text-cyan-400"
          />

          <h2 className="text-xl font-semibold text-white">
            AI Forensic Analysis
          </h2>

          <p className="mt-2 text-slate-400">
            Run AI/NLP analysis on the forensic evidence
            associated with this device.
          </p>

          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="mt-6 inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-5 py-3 font-semibold text-white hover:bg-cyan-500 disabled:opacity-50"
          >
            {isFetching && (
              <Loader2
                size={18}
                className="animate-spin"
              />
            )}

            Analyze Evidence
          </button>

        </div>
      </Card>
    );
  }

  if (isLoading || isFetching) {
    return (
      <Card>
        <div className="p-10 text-center text-slate-400">
          <Loader2
            size={32}
            className="mx-auto mb-3 animate-spin text-cyan-400"
          />

          Running AI analysis...
        </div>
      </Card>
    );
  }

  if (isError || !data) {
    return (
      <Card>
        <div className="p-8 text-center">

          <AlertTriangle
            size={36}
            className="mx-auto mb-3 text-red-400"
          />

          <h2 className="text-lg font-semibold text-white">
            AI Analysis Failed
          </h2>

          <p className="mt-2 text-slate-400">
            Unable to analyze the evidence.
          </p>

          <button
            onClick={() => refetch()}
            className="mt-4 rounded-lg bg-cyan-600 px-4 py-2 text-white"
          >
            Try Again
          </button>

        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-6">

      <Card>

        <div className="flex items-start justify-between">

          <div className="flex gap-4">

            <div className="rounded-lg bg-cyan-500/10 p-3">
              <Brain className="text-cyan-400" />
            </div>

            <div>
              <h2 className="text-xl font-semibold text-white">
                AI Forensic Analysis
              </h2>

              <p className="mt-1 text-slate-400">
                {data.summary}
              </p>
            </div>

          </div>

          <div className="flex items-center gap-2 text-green-400">
            <CheckCircle2 size={18} />
            {data.status}
          </div>

        </div>

      </Card>

      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">

        <Stat
          icon={<MessageSquare />}
          label="Evidence"
          value={data.statistics.totalEvidence}
        />

        <Stat
          icon={<Phone />}
          label="Communications"
          value={data.statistics.communications}
        />

        <Stat
          icon={<MapPin />}
          label="Locations"
          value={data.statistics.locations}
        />

        <Stat
          icon={<Phone />}
          label="Unique Phones"
          value={data.statistics.uniquePhones}
        />

        <Stat
          icon={<Users />}
          label="Contacts"
          value={data.statistics.contacts}
        />

      </div>

      <div className="grid lg:grid-cols-2 gap-6">

        <Card>

          <h3 className="mb-4 text-lg font-semibold text-white">
            Extracted Entities
          </h3>

          <EntityList
            title="Phone Numbers"
            values={data.entities.phoneNumbers}
          />

          <EntityList
            title="Contacts"
            values={data.entities.contacts}
          />

          <EntityList
            title="Locations"
            values={data.entities.locations}
          />

        </Card>

        <Card>

          <h3 className="mb-4 text-lg font-semibold text-white">
            Investigation Findings
          </h3>

          <div className="space-y-3">

            {data.findings.map((finding, index) => (
              <div
                key={index}
                className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-sm text-slate-300"
              >
                <span className="mr-2 text-cyan-400">
                  {index + 1}.
                </span>

                {finding}
              </div>
            ))}

          </div>

        </Card>

      </div>

      <Card>

        <h3 className="mb-4 text-lg font-semibold text-white">
          Keywords
        </h3>

        {data.keywords.length === 0 ? (
          <p className="text-slate-500">
            No keywords extracted.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {data.keywords.map((keyword) => (
              <span
                key={keyword}
                className="rounded-full bg-cyan-500/10 px-3 py-1 text-sm text-cyan-300"
              >
                {keyword}
              </span>
            ))}
          </div>
        )}

      </Card>

    </div>
  );
}

function Stat({
  icon,
  label,
  value
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
}) {
  return (
    <Card>
      <div className="text-cyan-400 mb-2">
        {icon}
      </div>

      <p className="text-sm text-slate-400">
        {label}
      </p>

      <p className="mt-1 text-2xl font-bold text-white">
        {value}
      </p>
    </Card>
  );
}

function EntityList({
  title,
  values
}: {
  title: string;
  values: string[];
}) {
  return (
    <div className="mb-5">

      <p className="mb-2 text-sm font-medium text-slate-400">
        {title}
      </p>

      {values.length === 0 ? (
        <span className="text-sm text-slate-600">
          None detected
        </span>
      ) : (
        <div className="flex flex-wrap gap-2">

          {values.map((value) => (
            <span
              key={value}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm text-slate-200"
            >
              {value}
            </span>
          ))}

        </div>
      )}

    </div>
  );
}