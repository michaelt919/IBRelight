﻿using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Media.Media3D;
using Tetzlaff.ReflectanceAcquisition.Pipeline.DataModels;
using Tetzlaff.ReflectanceAcquisition.Pipeline.Math;

namespace Tetzlaff.ReflectanceAcquisition.LightField.DataModels
{
    public class LightFieldDirectory : ILightField
    {
        public int ViewCount { get; private set; }
        public string DirectoryPath { get; private set; }

        public bool Force1To1AspectRatio { get; set; }

        private StreamWriter viewSetStreamWriter = null;
        private StreamWriter logFileStreamWriter = null;

        public LightFieldDirectory(string directoryPath)
        {
            this.ViewCount = 0;
            this.DirectoryPath = directoryPath;
            this.viewSetStreamWriter = new StreamWriter(directoryPath + "\\default.vset");
            this.logFileStreamWriter = new StreamWriter(directoryPath + "\\log.txt");

            this.Force1To1AspectRatio = false;
        }

        public void AddView(IColorFrame view, ICameraPose cameraPose, double alignmentEnergy)
        {
            if (this.AddViewSetEntry(cameraPose, view.CameraProjection, alignmentEnergy))
            {
                this.SaveLightFieldView(view, cameraPose);
            }
        }

        /// <summary>
        /// Completes the view set file, and closes the streams for both the view set file and the log file.
        /// </summary>
        public void FinalizeReflectance()
        {
            if (viewSetStreamWriter != null)
            {
                for (int i = 0; i < this.ViewCount; i++)
                {
                    viewSetStreamWriter.WriteLine("v\t" + i + "\t0\t0\tviews/" + string.Format("{0:D4}", i + 1) + ".png");
                }
                viewSetStreamWriter.Flush();
                viewSetStreamWriter.Close();
            }
            if (logFileStreamWriter != null)
            {
                logFileStreamWriter.Flush();
                logFileStreamWriter.Close();
            }
        }

        /// <summary>
        /// Saves the current image in the color buffer as a view for the light field being created
        /// </summary>
        private void SaveLightFieldView(IColorFrame colorFrame, ICameraPose cameraPose)
        {
            int viewIndex = this.ViewCount;

            if (!Directory.Exists(DirectoryPath + "\\views"))
            {
                // Create the view directory if it doesn't already exist
                Directory.CreateDirectory(DirectoryPath + "\\views");
            }
            if (DirectoryPath != null)
            {
                string path = DirectoryPath + "\\views\\" + string.Format("{0:D4}", viewIndex) + ".png";

                int width = this.Force1To1AspectRatio ? colorFrame.Height : colorFrame.Width;
                int height = colorFrame.Height;
                int margin = this.Force1To1AspectRatio ? (colorFrame.Width - colorFrame.Height) * 2 : 0; // * 4 / 2 = * 2 for four components; / 2 for two margins

                // Now we copy the pixels one by one into the Bitmap object, which is slower, but allows to save as a PNG file
                Bitmap bitmap = new Bitmap(width, height);
                int i = 0;
                for (int y = 0; y < height; y++)
                {
                    i += margin;
                    for (int x = width - 1; x >= 0; x--)
                    {
                        int b = colorFrame.RawPixels[i++];
                        int g = colorFrame.RawPixels[i++];
                        int r = colorFrame.RawPixels[i++];
                        int a = colorFrame.RawPixels[i++];
                        bitmap.SetPixel(x, y, Color.FromArgb(255, r, g, b));
                    }
                    i += margin; 
                }
                bitmap.Save(path);
            }
        }

        /// <summary>
        /// Add an entry for the current camera pose to the view set (vset) file
        /// </summary>
        /// <param name="m">The camera-to-world transformation matrix</param>
        /// <returns>true if added successfully, false if no entry was added</returns>
        private bool AddViewSetEntry(ICameraPose cameraPose, ICameraProjection cameraProjection, double alignmentEnergy)
        {
            if (cameraProjection != null && cameraPose != null && viewSetStreamWriter != null)
            {
                if (cameraProjection.AspectRatio < 1.0 && this.Force1To1AspectRatio)
                {
                    throw new ArgumentException("Cannot force 1:1 aspect ratio when the original aspect ratio is less than 1.0");
                }

                if (this.ViewCount == 0)
                {
                    viewSetStreamWriter.WriteLine("c\t" + cameraProjection.NearPlane + "\t" + cameraProjection.FarPlane);

                    // With a typical Kinect 2.0 sensor, the aspect ratio should be 16:9, 
                    // and horizontal fov should be 62(?) degrees, which corresponds to a sensor width of 42 in Blender.
                    // Currently, there seem to be issues with non 1:1 aspect ratios in the light field morphing code base,
                    // so this line will probably need to be changed in the vset file manually to make it work.
                    viewSetStreamWriter.WriteLine("f\t0\t0\t" +
                        (this.Force1To1AspectRatio ? 1.0 : cameraProjection.AspectRatio) + "\t" +
                        (this.Force1To1AspectRatio ? cameraProjection.VerticalFieldOfView : cameraProjection.HorizontalFieldOfView));
                }

                // Write the raw matrix to the log file for debugging purposes
                logFileStreamWriter.Write("View " + (this.ViewCount + 1) + ":\t");

                Matrix4 m = cameraPose.Matrix;
                logFileStreamWriter.Write("Raw matrix values:\t" +
                    m.M11 + "\t" + m.M12 + "\t" + m.M13 + "\t" + m.M14 + "\t" +
                    m.M21 + "\t" + m.M22 + "\t" + m.M23 + "\t" + m.M24 + "\t" +
                    m.M31 + "\t" + m.M32 + "\t" + m.M33 + "\t" + m.M34 + "\t" +
                    m.M41 + "\t" + m.M42 + "\t" + m.M43 + "\t" + m.M44 + "\t"
                );

                // Take the upper 3x3 matrix transposed (the inverted rotation matrix) and negate the values that are affected by flipping across the x-axis
                Matrix3D r = new Matrix3D(m.M11, -m.M21, -m.M31, 0.0, -m.M12, m.M22, m.M32, 0.0, -m.M13, m.M23, m.M33, 0.0, 0.0, 0.0, 0.0, 1.0);

                // Flip across the x-axis, shift by 0.05 (approximate offset between the depth and RGB cameras), then rotate by the inverted rotation matrix
                Vector3D pos = new Vector3D(-m.M41 + 0.05, m.M42, m.M43);
                pos = r.Transform(pos);

                // Convert rotation matrix to quaternion
                double trace = r.M11 + r.M22 + r.M33;
                double w, x, y, z;
                if (trace > 0)
                {
                    double s = 0.5 / Math.Sqrt(trace + 1.0);
                    w = 0.25 / s;
                    x = (r.M32 - r.M23) * s;
                    y = (r.M13 - r.M31) * s;
                    z = (r.M21 - r.M12) * s;
                    logFileStreamWriter.Write("\tQuaternion case 1");
                }
                else
                {
                    if (r.M11 > r.M22 && r.M11 > r.M33)
                    {
                        double s = 2.0 * Math.Sqrt(1.0 + r.M11 - r.M22 - r.M33);
                        w = (r.M32 - r.M23) / s;
                        x = 0.25 * s;
                        y = (r.M12 + r.M21) / s;
                        z = (r.M13 + r.M31) / s;
                        logFileStreamWriter.Write("\tQuaternion case 2");
                    }
                    else if (r.M22 > r.M33)
                    {
                        double s = 2.0 * Math.Sqrt(1.0 + r.M22 - r.M11 - r.M33);
                        w = (r.M13 - r.M31) / s;
                        x = (r.M12 + r.M21) / s;
                        y = 0.25 * s;
                        z = (r.M23 + r.M32) / s;
                        logFileStreamWriter.Write("\tQuaternion case 3");
                    }
                    else
                    {
                        double s = 2.0 * Math.Sqrt(1.0 + r.M33 - r.M11 - r.M22);
                        w = (r.M21 - r.M12) / s;
                        x = (r.M13 + r.M31) / s;
                        y = (r.M23 + r.M32) / s;
                        z = 0.25 * s;
                        logFileStreamWriter.Write("\tQuaternion case 4");
                    }
                }

                // Write the camera offset, rotation quaternion, and image filename to the vset file
                viewSetStreamWriter.WriteLine("p\t" + pos.X + "\t" + pos.Y + "\t" + pos.Z + "\t" +
                    x + "\t" + y + "\t" + z + "\t" + w + "\tviews\\" + string.Format("{0:D4}", this.ViewCount + 1) + ".png"
                );

                logFileStreamWriter.WriteLine("\tAlignment energy: " + alignmentEnergy);
                this.ViewCount++;
                return true;
            }
            else return false;
        }
    }
}
