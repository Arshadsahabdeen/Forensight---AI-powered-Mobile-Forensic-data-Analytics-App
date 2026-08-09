import { useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  Edit,
  Smartphone,
  Shield,
  User,
  FileText,
  Calendar,
  Cpu
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { getDevice } from "../../services/deviceService";

export function DeviceDetailPage() {

  const { id } = useParams();

  const navigate = useNavigate();

  const { data, isLoading } = useQuery({

    queryKey: ["device", id],

    queryFn: () => getDevice(Number(id)),

    enabled: !!id

  });

  if (isLoading) {

    return (

      <Card>

        <div className="p-10 text-center text-slate-400">

          Loading Device...

        </div>

      </Card>

    );

  }

  if (!data) {

    return (

      <Card>

        <div className="p-10 text-center text-red-400">

          Device not found.

        </div>

      </Card>

    );

  }

  const device = data.device;

  return (

    <div className="space-y-6">

      <div className="flex items-center justify-between">

        <div>

          <h1 className="text-3xl font-bold text-white">

            {device.deviceName}

          </h1>

          <p className="text-slate-400 mt-2">

            Complete forensic device information

          </p>

        </div>

        <div className="flex gap-3">

          <button

            onClick={() => navigate("/devices")}

            className="rounded-lg border border-slate-700 px-4 py-2 text-white"

          >

            <ArrowLeft size={18} className="inline mr-2"/>

            Back

          </button>

          <button

            onClick={() => navigate(`/devices/${device.id}/edit`)}

            className="rounded-lg bg-cyan-600 px-4 py-2 text-white"

          >

            <Edit size={18} className="inline mr-2"/>

            Edit

          </button>

        </div>

      </div>

      {/* Dashboard Cards */}

      <div className="grid grid-cols-1 md:grid-cols-4 gap-5">

        <Card>

          <Smartphone className="text-cyan-400 mb-3"/>

          <p className="text-slate-400">

            Device Status

          </p>

          <h2 className="text-xl font-bold text-white mt-2">

            {device.acquisitionStatus.replace(/_/g, " ")}

          </h2>

        </Card>

        <Card>

          <Shield className="text-green-400 mb-3"/>

          <p className="text-slate-400">

            Evidence

          </p>

          <h2 className="text-3xl font-bold text-white mt-2">

            {data.evidenceCount}

          </h2>

        </Card>

        <Card>

          <FileText className="text-yellow-400 mb-3"/>

          <p className="text-slate-400">

            Reports

          </p>

          <h2 className="text-3xl font-bold text-white mt-2">

            {data.reportCount}

          </h2>

        </Card>

        <Card>

          <Calendar className="text-purple-400 mb-3"/>

          <p className="text-slate-400">

            Timeline

          </p>

          <h2 className="text-3xl font-bold text-white mt-2">

            {data.timelineCount}

          </h2>

        </Card>

      </div>

      <div className="grid lg:grid-cols-2 gap-6">

        <Card>

          <h2 className="text-xl font-semibold text-white mb-5">

            Device Information

          </h2>

          <div className="space-y-3 text-sm">

            <Row title="Manufacturer" value={device.manufacturer}/>

            <Row title="Brand" value={device.brand}/>

            <Row title="Model" value={device.model}/>

            <Row title="IMEI" value={device.imei}/>

            <Row title="Serial Number" value={device.serialNumber}/>

            <Row title="Operating System" value={device.operatingSystem}/>

            <Row title="OS Version" value={device.osVersion}/>

            <Row title="Device Type" value={device.deviceType}/>

            <Row title="Acquisition Method" value={device.acquisitionMethod}/>

          </div>

        </Card>

        <Card>

          <h2 className="text-xl font-semibold text-white mb-5">

            Investigation Information

          </h2>

          <div className="space-y-3 text-sm">

            <Row title="Case Number" value={device.caseNumber}/>

            <Row title="Owner" value={device.ownerName}/>

            <Row title="Owner Contact" value={device.ownerContact}/>

            <Row title="Investigator" value={device.investigator}/>

            <Row title="Seized Date" value={device.seizedDate}/>

            <Row title="Received Date" value={device.receivedDate}/>

            <Row title="Created" value={new Date(device.createdAt).toLocaleString()}/>

            <Row title="Updated" value={new Date(device.updatedAt).toLocaleString()}/>

          </div>

        </Card>

      </div>

      <Card>

        <h2 className="text-xl font-semibold text-white mb-4">

          Investigation Notes

        </h2>

        <div className="rounded-lg bg-slate-900 p-5 text-slate-300 whitespace-pre-wrap">

          {device.notes || "No notes available."}

        </div>

      </Card>

    </div>

  );

}

function Row({

  title,

  value

}:{

  title:string,

  value:any

}){

  return(

    <div className="flex justify-between border-b border-slate-800 pb-2">

      <span className="text-slate-400">

        {title}

      </span>

      <span className="font-medium text-white">

        {value || "-"}

      </span>

    </div>

  );

}