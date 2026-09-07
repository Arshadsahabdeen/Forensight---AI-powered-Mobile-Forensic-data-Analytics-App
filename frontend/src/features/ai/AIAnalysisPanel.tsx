import { useState } from "react";
import {
  AlertTriangle,
  Brain,
  CheckCircle2,
  Loader2,
  MapPin,
  MessageSquare,
  Phone,
  RotateCcw,
  Tags,
  Users,
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { Button } from "../../components/ui/Button";
import {
  analyzeDevice,
  type AIAnalysisResponse,
} from "../../services/aiService";

interface AIAnalysisPanelProps {
  deviceId: number;
}

export function AIAnalysisPanel({
  deviceId,
}: AIAnalysisPanelProps) {
  const [analysis, setAnalysis] =
    useState<AIAnalysisResponse | null>(null);

  const [errorMessage, setErrorMessage] =
    useState<string | null>(null);

  const [isAnalyzing, setIsAnalyzing] =
    useState(false);

  async function runAnalysis() {
    setIsAnalyzing(true);
    setErrorMessage(null);

    try {
      const result =
        await analyzeDevice(deviceId);

      setAnalysis(result);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to analyze device evidence."
      );
    } finally {
      setIsAnalyzing(false);
    }
  }

  /*
   * Initial state
   */

  if (!analysis && !isAnalyzing && !errorMessage) {
    return (
      <Card>
        <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border border-cyan-500/20 bg-cyan-500/10 text-cyan-300">
              <Brain size={24} />
            </div>

            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-cyan-300">
                AI Analysis
              </p>

              <h2 className="mt-1 text-xl font-semibold text-white">
                Analyze Device Evidence
              </h2>

              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
                Analyze the extracted forensic evidence
                from this device to identify entities,
                communications, locations, keywords,
                patterns and investigation findings.
              </p>
            </div>
          </div>

          <Button
            onClick={runAnalysis}
            disabled={isAnalyzing}
          >
            <Brain size={16} />
            Run AI Analysis
          </Button>
        </div>
      </Card>
    );
  }

  /*
   * Loading state
   */

  if (isAnalyzing) {
    return (
      <Card>
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-full border border-cyan-500/20 bg-cyan-500/10">
            <Loader2
              size={28}
              className="animate-spin text-cyan-400"
            />
          </div>

          <h2 className="mt-5 text-lg font-semibold text-white">
            Running AI forensic analysis
          </h2>

          <p className="mt-2 max-w-lg text-sm leading-6 text-slate-500">
            The analysis service is processing the
            extracted evidence associated with this
            device.
          </p>

          <div className="mt-6 flex flex-wrap justify-center gap-2 text-xs">
            <span className="rounded-full bg-slate-900 px-3 py-1.5 text-slate-400">
              Evidence correlation
            </span>

            <span className="rounded-full bg-slate-900 px-3 py-1.5 text-slate-400">
              Entity extraction
            </span>

            <span className="rounded-full bg-slate-900 px-3 py-1.5 text-slate-400">
              Timeline analysis
            </span>

            <span className="rounded-full bg-slate-900 px-3 py-1.5 text-slate-400">
              Finding generation
            </span>
          </div>
        </div>
      </Card>
    );
  }

  /*
   * Error state
   */

  if (errorMessage) {
    return (
      <Card>
        <div className="flex flex-col items-center justify-center py-10 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full border border-red-500/20 bg-red-500/10 text-red-400">
            <AlertTriangle size={24} />
          </div>

          <h2 className="mt-4 text-lg font-semibold text-white">
            AI Analysis Failed
          </h2>

          <p className="mt-2 max-w-lg text-sm leading-6 text-slate-500">
            {errorMessage}
          </p>

          <Button
            onClick={runAnalysis}
            className="mt-5"
          >
            <RotateCcw size={16} />
            Retry Analysis
          </Button>
        </div>
      </Card>
    );
  }

  /*
   * Successful analysis
   */

  if (!analysis) {
    return null;
  }

  const success =
    analysis.status.toUpperCase() ===
    "SUCCESS";

  return (
    <div className="space-y-6">
      {/* Analysis header */}

      <Card>
        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border border-cyan-500/20 bg-cyan-500/10 text-cyan-300">
              <Brain size={24} />
            </div>

            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-cyan-300">
                AI Analysis
              </p>

              <h2 className="mt-1 text-xl font-semibold text-white">
                Evidence Intelligence Summary
              </h2>

              <p className="mt-3 max-w-4xl text-sm leading-7 text-slate-300">
                {analysis.summary ||
                  "The analysis service returned no summary."}
              </p>
            </div>
          </div>

          <div
            className={`inline-flex w-fit shrink-0 items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-medium ${
              success
                ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                : "border-amber-500/30 bg-amber-500/10 text-amber-300"
            }`}
          >
            <CheckCircle2 size={17} />

            {success
              ? "Analysis Complete"
              : analysis.status}
          </div>
        </div>
      </Card>

      {/* Statistics */}

      <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
        <Stat
          icon={<MessageSquare size={18} />}
          label="Total Evidence"
          value={
            analysis.statistics
              .totalEvidence
          }
        />

        <Stat
          icon={<Phone size={18} />}
          label="Communications"
          value={
            analysis.statistics
              .communications
          }
        />

        <Stat
          icon={<MapPin size={18} />}
          label="Locations"
          value={
            analysis.statistics
              .locations
          }
        />

        <Stat
          icon={<Phone size={18} />}
          label="Unique Phones"
          value={
            analysis.statistics
              .uniquePhones
          }
        />

        <Stat
          icon={<Users size={18} />}
          label="Contacts"
          value={
            analysis.statistics
              .contacts
          }
        />
      </div>

      {/* Entities */}

      <Card>
        <div className="mb-6">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
            Correlated Entities
          </h3>

          <p className="mt-1 text-xs text-slate-600">
            Entities extracted and correlated from the
            device evidence.
          </p>
        </div>

        <div className="grid gap-6 md:grid-cols-3">
          <EntityList
            title="Phone Numbers"
            values={
              analysis.entities
                .phoneNumbers
            }
          />

          <EntityList
            title="Contacts"
            values={
              analysis.entities
                .contacts
            }
          />

          <EntityList
            title="Locations"
            values={
              analysis.entities
                .locations
            }
          />
        </div>
      </Card>

      {/* Findings */}

      <Card>
        <div className="mb-5">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
            Key Findings
          </h3>

          <p className="mt-1 text-xs text-slate-600">
            Findings returned by the analysis service.
          </p>
        </div>

        {analysis.findings.length ===
        0 ? (
          <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-5 text-sm text-slate-500">
            No findings were returned by the
            analysis service.
          </div>
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {analysis.findings.map(
              (finding, index) => (
                <div
                  key={`${finding}-${index}`}
                  className="rounded-lg border border-slate-800 bg-slate-900/70 p-4"
                >
                  <div className="flex items-start gap-3">
                    <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-cyan-500/10 text-xs font-semibold text-cyan-300">
                      {index + 1}
                    </div>

                    <p className="text-sm leading-6 text-slate-300">
                      {finding}
                    </p>
                  </div>
                </div>
              )
            )}
          </div>
        )}
      </Card>

      {/* Keywords */}

      {analysis.keywords.length >
        0 && (
        <Card>
          <div className="mb-4 flex items-center gap-2">
            <Tags
              size={17}
              className="text-cyan-300"
            />

            <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
              Extracted Keywords
            </h3>
          </div>

          <div className="flex flex-wrap gap-2">
            {analysis.keywords.map(
              (keyword, index) => (
                <span
                  key={`${keyword}-${index}`}
                  className="rounded-full border border-cyan-500/10 bg-cyan-500/10 px-3 py-1.5 text-sm text-cyan-300"
                >
                  {keyword}
                </span>
              )
            )}
          </div>
        </Card>
      )}

      {/* Re-run */}

      <div className="flex justify-end">
        <Button
          onClick={runAnalysis}
          disabled={isAnalyzing}
          className="bg-slate-900 text-slate-300 hover:bg-slate-800"
        >
          <RotateCcw size={16} />
          Run Analysis Again
        </Button>
      </div>
    </div>
  );
}

function Stat({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
}) {
  return (
    <Card>
      <div className="flex items-center gap-2 text-cyan-400">
        {icon}

        <span className="text-xs text-slate-500">
          {label}
        </span>
      </div>

      <p className="mt-3 text-2xl font-bold text-white">
        {value}
      </p>
    </Card>
  );
}

function EntityList({
  title,
  values,
}: {
  title: string;
  values: string[];
}) {
  return (
    <div>
      <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-600">
        {title}
      </p>

      {values.length === 0 ? (
        <span className="text-sm text-slate-600">
          None detected
        </span>
      ) : (
        <div className="flex max-h-48 flex-wrap gap-2 overflow-y-auto">
          {values.map(
            (value, index) => (
              <span
                key={`${value}-${index}`}
                className="rounded-md border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200"
              >
                {value}
              </span>
            )
          )}
        </div>
      )}
    </div>
  );
}