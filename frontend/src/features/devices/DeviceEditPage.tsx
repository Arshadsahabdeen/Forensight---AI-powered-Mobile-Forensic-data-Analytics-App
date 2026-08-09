import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Card } from "../../components/ui/Card";
import DeviceForm from "./DeviceForm";
import { getDevice, updateDevice } from "../../services/deviceService";

export function DeviceEditPage() {

  const { id } = useParams();

  const navigate = useNavigate();

  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({

    queryKey: ["device", id],

    queryFn: () => getDevice(Number(id)),

    enabled: !!id

  });

  const mutation = useMutation({

    mutationFn: (values: any) =>
      updateDevice(Number(id), values),

    onSuccess: () => {

      queryClient.invalidateQueries({

        queryKey: ["devices"]

      });

      queryClient.invalidateQueries({

        queryKey: ["device", id]

      });

      navigate("/devices");

    }

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

  return (

    <div className="space-y-6">

      <div>

        <h1 className="text-3xl font-bold text-white">

          Edit Device

        </h1>

        <p className="mt-2 text-slate-400">

          Update forensic device information.

        </p>

      </div>

      <Card>

        <DeviceForm

          initialValues={data.device}

          loading={mutation.isPending}

          onSubmit={(values) => mutation.mutate(values)}

        />

      </Card>

    </div>

  );

}