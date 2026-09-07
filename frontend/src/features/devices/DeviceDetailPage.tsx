import { useNavigate, useParams } from "react-router-dom";
import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  ArrowLeft,
  Calendar,
  Edit,
  FileText,
  HardDrive,
  Loader2,
  Shield,
  Trash2,
  Brain,
} from "lucide-react";
import type { ReactNode } from "react";

import { Card } from "../../components/ui/Card";
import { Button } from "../../components/ui/Button";
import {
  getDevice,
  deleteDevice,
} from "../../services/deviceService";
import { friendlyError } from "../../services/errors";
import { InvestigationTabs } from "./components/InvestigationTabs";

function formatValue(
  value: string | null | undefined
) {
  return value && value.trim()
    ? value
    : "-";
}

function formatStatus(
  value: string | null | undefined
) {
  return value
    ? value.replace(/\_/g, " ")
    : "-";
}

function formatDate(
  value: string | null | undefined
) {
  return value
    ? new Date(value).toLocaleString()
    : "-";
}

export function DeviceDetailPage() {
  const { id } = useParams();

  const navigate = useNavigate();

  const queryClient =
    useQueryClient();

  const deviceId = Number(id);

  const {
    data,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ["device", deviceId],
    queryFn: () => getDevice(deviceId),
    enabled: Number.isFinite(deviceId),
  });

  const deleteMutation =
    useMutation({
      mutationFn: () =>
        deleteDevice(deviceId),

      onSuccess: async () => {
        await queryClient.invalidateQueries({
          queryKey: ["devices"],
        });

        navigate("/devices", {
          replace: true,
        });
      },
    });

  if (isLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="flex items-center gap-3 text-slate-400">
          <Loader2
            size={20}
            className="animate-spin"
          />
          Loading device details...
        </div>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="p-6">
        <Card>
          <div className="py-10 text-center">
            <p className="text-sm text-rose-300">
              {friendlyError(
                error,
                "Unable to load device details."
              )}
            </p>

            <Button
              className="mt-4"
              onClick={() => refetch()}
            >
              Retry
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  const device = data.device;

  return (
    <div className="space-y-6 p-6">

      {/* =====================================================
          HEADER
      ===================================================== */}

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">

        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-cyan-300">
            Device Investigation
          </p>

          <h1 className="mt-1 text-3xl font-bold text-white">
            {formatValue(device.deviceName)}
          </h1>

          <p className="mt-2 text-sm text-slate-400">
            Case{" "}
            {formatValue(device.caseNumber)}
            {" | "}
            Status{" "}
            {formatStatus(
              device.acquisitionStatus
            )}
          </p>
        </div>

        <div className="flex flex-wrap gap-3">

          {/* Back */}

          <Button
            onClick={() =>
              navigate("/devices")
            }
            className="bg-transparent text-slate-200 hover:bg-white/10"
          >
            <ArrowLeft size={16} />
            Back
          </Button>

          {/* Edit */}

          <Button
            onClick={() =>
              navigate(
                `/devices/${device.id}/edit`
              )
            }
            className="bg-cyan-600 text-white hover:bg-cyan-500"
          >
            <Edit size={16} />
            Edit
          </Button>

          {/* Generate Report */}

          <Button
            onClick={() =>
              navigate(
                `/reports/${device.id}`
              )
            }
            className="bg-violet-600 text-white hover:bg-violet-500"
          >
            <FileText size={16} />
            Generate Report
          </Button>

          {/* Delete */}

          <Button
            onClick={() => {
              if (
                window.confirm(
                  "Delete this device and its extracted evidence?"
                )
              ) {
                deleteMutation.mutate();
              }
            }}
            disabled={
              deleteMutation.isPending
            }
            className="bg-red-600 text-white hover:bg-red-500"
          >
            {deleteMutation.isPending ? (
              <Loader2
                size={16}
                className="animate-spin"
              />
            ) : (
              <Trash2 size={16} />
            )}

            Delete
          </Button>

        </div>
      </div>

      {/* =====================================================
          DELETE ERROR
      ===================================================== */}

      {deleteMutation.error && (
        <Card>
          <p className="text-sm text-rose-300">
            {friendlyError(
              deleteMutation.error,
              "Unable to delete device."
            )}
          </p>
        </Card>
      )}

      {/* =====================================================
          METRICS
      ===================================================== */}

      <div className="grid grid-cols-1 gap-5 md:grid-cols-4">

        <MetricCard
          icon={
            <Shield className="text-cyan-400" />
          }
          label="Acquisition Status"
          value={formatStatus(
            device.acquisitionStatus
          )}
        />

        <MetricCard
          icon={
            <HardDrive className="text-emerald-400" />
          }
          label="Evidence Count"
          value={String(
            data.evidenceCount
          )}
        />

        <MetricCard
          icon={
            <FileText className="text-amber-400" />
          }
          label="Report Count"
          value={String(
            data.reportCount
          )}
        />

        <MetricCard
          icon={
            <Calendar className="text-violet-400" />
          }
          label="Timeline Count"
          value={String(
            data.timelineCount
          )}
        />

      </div>

      {/* =====================================================
          QUICK INVESTIGATION ACTIONS
      ===================================================== */}

      <Card>
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">

          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-cyan-300">
              Investigation Workflow
            </p>

            <h2 className="mt-1 text-lg font-semibold text-white">
              Analyze and generate investigation report
            </h2>

            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
              Review the extracted evidence and
              timeline, run AI analysis, then generate
              the complete investigation report from
              the device's real forensic data.
            </p>
          </div>

          <div className="flex flex-wrap gap-3">

            <Button
              onClick={() => {
                const element =
                  document.getElementById(
                    "investigation-tabs"
                  );

                element?.scrollIntoView({
                  behavior: "smooth",
                  block: "start",
                });
              }}
              className="bg-slate-900 text-slate-300 hover:bg-slate-800"
            >
              <Brain size={16} />
              Analyze Evidence
            </Button>

            <Button
              onClick={() =>
                navigate(
                  `/reports/${device.id}`
                )
              }
              className="bg-violet-600 text-white hover:bg-violet-500"
            >
              <FileText size={16} />
              View Report
            </Button>

          </div>
        </div>
      </Card>

      {/* =====================================================
          DEVICE INFORMATION
      ===================================================== */}

      <div className="grid gap-6 lg:grid-cols-2">

        <Card>
          <h2 className="mb-5 text-xl font-semibold text-white">
            Device Information
          </h2>

          <div className="space-y-3 text-sm">

            <Row
              title="Device Name"
              value={device.deviceName}
            />

            <Row
              title="Manufacturer"
              value={device.manufacturer}
            />

            <Row
              title="Brand"
              value={device.brand}
            />

            <Row
              title="Model"
              value={device.model}
            />

            <Row
              title="IMEI"
              value={device.imei}
            />

            <Row
              title="Serial Number"
              value={device.serialNumber}
            />

            <Row
              title="Operating System"
              value={
                device.operatingSystem
              }
            />

            <Row
              title="OS Version"
              value={device.osVersion}
            />

            <Row
              title="Device Type"
              value={device.deviceType}
            />

            <Row
              title="Acquisition Method"
              value={formatStatus(
                device.acquisitionMethod
              )}
            />

            <Row
              title="Acquisition Status"
              value={formatStatus(
                device.acquisitionStatus
              )}
            />

          </div>
        </Card>

        <Card>
          <h2 className="mb-5 text-xl font-semibold text-white">
            Investigation Information
          </h2>

          <div className="space-y-3 text-sm">

            <Row
              title="Case Number"
              value={device.caseNumber}
            />

            <Row
              title="Owner"
              value={device.ownerName}
            />

            <Row
              title="Owner Contact"
              value={device.ownerContact}
            />

            <Row
              title="Investigator"
              value={device.investigator}
            />

            <Row
              title="Seized Date"
              value={device.seizedDate}
            />

            <Row
              title="Received Date"
              value={device.receivedDate}
            />

            <Row
              title="Created"
              value={formatDate(
                device.createdAt
              )}
            />

            <Row
              title="Updated"
              value={formatDate(
                device.updatedAt
              )}
            />

          </div>
        </Card>

      </div>

      {/* =====================================================
          NOTES
      ===================================================== */}

      <Card>
        <h2 className="mb-4 text-xl font-semibold text-white">
          Investigation Notes
        </h2>

        <div className="rounded-lg bg-slate-900 p-5 whitespace-pre-wrap text-slate-300">
          {formatValue(device.notes)}
        </div>
      </Card>

      {/* =====================================================
          INVESTIGATION TABS
      ===================================================== */}

      <div id="investigation-tabs">
        <InvestigationTabs
          deviceId={deviceId}
          deviceDetail={data}
        />
      </div>

    </div>
  );
}

function MetricCard({
  icon,
  label,
  value,
}: {
  icon: ReactNode;
  label: string;
  value: string;
}) {
  return (
    <Card>
      <div className="flex items-center gap-3">

        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-900">
          {icon}
        </div>

        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">
            {label}
          </p>

          <p className="mt-1 text-lg font-semibold text-white">
            {value}
          </p>
        </div>

      </div>
    </Card>
  );
}

function Row({
  title,
  value,
}: {
  title: string;
  value:
    | string
    | null
    | undefined;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-800/70 pb-3 last:border-0">

      <span className="text-slate-500">
        {title}
      </span>

      <span className="max-w-[60%] break-all text-right text-slate-200">
        {formatValue(value)}
      </span>

    </div>
  );
}